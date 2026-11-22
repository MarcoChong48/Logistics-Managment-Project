package com.lms.backend.client.order;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_PICKED_UP;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ACCEPTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ENDED_UNPAID;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_REFUND_REQUIRED;
import static java.util.stream.Collectors.groupingBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.client.order.ClientOrderDetailsResponse.PaymentAttempt;
import com.lms.backend.client.order.ClientOrderDetailsResponse.Refund;
import com.lms.backend.client.order.ClientOrderListResponse.ItemSummary;
import com.lms.backend.client.order.ClientOrderListResponse.OrderSummary;
import com.lms.backend.client.order.ClientOrderReadRepository.OrderRow;
import com.lms.backend.client.order.ClientOrderReadRepository.PaymentRow;
import com.lms.backend.client.order.ClientOrderReadRepository.RefundRow;
import com.lms.backend.client.order.ClientOrderTrackingResponse.DeliveryAttempt;
import com.lms.backend.client.order.ClientOrderTrackingResponse.TrackingEvent;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedDeliveryFailureReason;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ClientOrderReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ClientOrderReadRepository orders;
    private final ClientOrderReleaseService cancellation;
    private final SharedCurrentUserReader currentUser;

    ClientOrderReadService(ClientOrderReadRepository orders, ClientOrderReleaseService cancellation,
            SharedCurrentUserReader currentUser) {
        this.orders = orders;
        this.cancellation = cancellation;
        this.currentUser = currentUser;
    }

    public ClientOrderListResponse list(int limit, String cursor) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "CLIENT_ORDER_PAGE_SIZE_INVALID",
                    "Choose a limit between 1 and 50.");
        }
        long clientId = currentUser.sharedReadCurrentUser().userID();
        var anchor = cursor == null ? null : requireOwned(clientId, cursor);
        var rows = orders.findPage(clientId, anchor, limit + 1);
        var page = rows.stream().limit(limit).map(row -> {
            var order = row.order();
            return new OrderSummary(order.reference(), order.createdAt().toInstant(), publicStatus(order.status()),
                    order.amount(), paymentDeadline(order), new ItemSummary(row.productCount(), row.quantity()));
        }).toList();
        String nextCursor = rows.size() > limit ? page.getLast().orderReference() : null;
        return new ClientOrderListResponse(page, nextCursor);
    }

    public ClientOrderDetailsResponse details(String reference) {
        var order = requireOwned(currentUser.sharedReadCurrentUser().userID(), reference);
        return new ClientOrderDetailsResponse(order.reference(), order.createdAt().toInstant(),
                publicStatus(order.status()), paymentDeadline(order), order.amount(), orders.findItems(order.id()),
                orders.findCurrentDeliveryDetails(order.id()).orElse(null), paymentAttempts(order.id()),
                cancellation.canCancelBeforePicking(order.id(), order.status()));
    }

    public ClientOrderTrackingResponse tracking(String reference) {
        var order = requireOwned(currentUser.sharedReadCurrentUser().userID(), reference);
        var fulfillment = orders.findFulfillment(order.id());
        var history = new ArrayList<TrackingEvent>();
        addEvent(history, "ORDER_PLACED", order.createdAt().toInstant(), null, null);
        addEvent(history, "PICKING_STARTED", fulfillment.pickingStartedAt(), null, null);
        addEvent(history, "PACKING_CONFIRMED", fulfillment.packingConfirmedAt(), null, null);
        addEvent(history, "PACKAGE_CREATED", fulfillment.packageCreatedAt(), null, null);

        var cycleByPickup = new HashMap<Long, Integer>();
        var cycleByList = new HashMap<Long, Integer>();
        int cycleCount = 0;
        for (var event : orders.findCustodyEvents(order.id())) {
            if (SHARED_CUSTODY_EVENT_PICKED_UP.equals(event.type())) {
                Long listId = event.pickupListId();
                Integer cycle = listId == null ? null : cycleByList.get(listId);
                if (cycle == null) {
                    cycle = ++cycleCount;
                    if (listId != null) {
                        cycleByList.put(listId, cycle);
                    }
                    addEvent(history, "PACKAGE_PICKED_UP", event.occurredAt(), null, cycle);
                }
                cycleByPickup.put(event.id(), cycle);
            } else {
                addEvent(history, "PACKAGE_RETURNED_TO_WAREHOUSE", event.occurredAt(), null, null);
            }
        }

        var attempts = new ArrayList<DeliveryAttempt>();
        for (var row : orders.findDeliveryAttempts(order.id())) {
            int number = attempts.size() + 1;
            Integer cycle = cycleByPickup.get(row.pickupEventId());
            String outcome = deliveryOutcome(row.outcome());
            String reason = "FAILED".equals(outcome) ? SharedDeliveryFailureReason.messageForCode(row.failureReason()) : null;
            attempts.add(new DeliveryAttempt(number, cycle, row.deliveryAddressVersion(), row.startedAt(), row.endedAt(),
                    outcome, reason));
            addEvent(history, "DELIVERY_ATTEMPT_STARTED", row.startedAt(), number, cycle);
            String endEvent = switch (outcome) {
                case "DELIVERED" -> "DELIVERY_COMPLETED";
                case "FAILED" -> "DELIVERY_FAILED";
                default -> "DELIVERY_ATTEMPT_ENDED";
            };
            addEvent(history, endEvent, row.endedAt(), number, cycle);
        }

        // On tied timestamps the order of insertion is stable, including the ordering within source queries.
        history.sort(Comparator.comparing(TrackingEvent::occurredAt));
        String status = publicStatus(order.status());
        return new ClientOrderTrackingResponse(order.reference(), status, statusLabel(status), null, false,
                history, attempts, paymentAttempts(order.id()));
    }

    private OrderRow requireOwned(long clientId, String reference) {
        if (reference.isBlank() || reference.length() > 60) {
            throw notFound();
        }
        return orders.findOwned(clientId, reference).orElseThrow(this::notFound);
    }

    private List<PaymentAttempt> paymentAttempts(long orderId) {
        var payments = orders.findPayments(orderId);
        if (payments.isEmpty()) {
            return List.of();
        }
        var refundsByPayment = orders.findRefunds(orderId).stream().collect(groupingBy(RefundRow::paymentId));
        return payments.stream().map(payment -> new PaymentAttempt(paymentStatus(payment), payment.createdAt(),
                payment.acceptedAt(), refundsByPayment.getOrDefault(payment.id(), List.of()).stream()
                        .map(ClientOrderReadService::refund).toList())).toList();
    }

    private static Refund refund(RefundRow row) {
        boolean complete = "COMPLETED".equals(row.status()) && row.completedAt() != null
                && !row.completedAt().isBefore(row.createdAt());
        String status = complete ? "COMPLETED" : switch (row.status()) {
            case "REQUESTED", "PENDING", "FAILED", "CANCELLED" -> row.status();
            default -> "UNKNOWN";
        };
        return new Refund(status, row.amount(), row.createdAt(), complete ? row.completedAt() : null);
    }

    private static void addEvent(List<TrackingEvent> history, String type, Instant when, Integer attempt,
            Integer cycle) {
        if (when != null) {
            history.add(new TrackingEvent(type, when, attempt, cycle));
        }
    }

    private static String paymentStatus(PaymentRow payment) {
        return switch (payment.status()) {
            case SHARED_PAYMENT_STATUS_ACCEPTED, SHARED_PAYMENT_STATUS_ENDED_UNPAID,
                    SHARED_PAYMENT_STATUS_REFUND_REQUIRED -> payment.status();
            case SHARED_PAYMENT_STATUS_PENDING -> payment.acceptedAt() == null ? SHARED_PAYMENT_STATUS_PENDING
                    : SHARED_PAYMENT_STATUS_ACCEPTED;
            default -> payment.acceptedAt() == null ? "UNKNOWN" : SHARED_PAYMENT_STATUS_ACCEPTED;
        };
    }

    private static String deliveryOutcome(String outcome) {
        return switch (outcome) {
            case "IN_PROGRESS", "DELIVERED", "FAILED" -> outcome;
            default -> "UNKNOWN";
        };
    }

    private static Instant paymentDeadline(OrderRow order) {
        return SharedOrderStatus.AWAITING_PAYMENT.name().equals(order.status()) ? order.deadline() : null;
    }

    private static String publicStatus(String status) {
        try {
            return SharedOrderStatus.valueOf(status).name();
        } catch (IllegalArgumentException unknown) {
            return "UNKNOWN";
        }
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "AWAITING_PAYMENT" -> "Awaiting payment";
            case "READY_FOR_PICKING" -> "Ready for picking";
            case "PICKING" -> "Picking";
            case "READY_FOR_PICKUP" -> "Ready for pickup";
            case "SHIPPED" -> "Shipped";
            case "OUT_FOR_DELIVERY" -> "Out for delivery";
            case "DELIVERY_FAILED" -> "Delivery failed";
            case "READY_FOR_REDELIVERY" -> "Ready for redelivery";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Cancelled";
            default -> "Status unavailable";
        };
    }

    private SharedRuleException notFound() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "CLIENT_ORDER_NOT_FOUND", "Order was not found.");
    }
}
