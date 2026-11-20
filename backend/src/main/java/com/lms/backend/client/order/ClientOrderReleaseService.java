package com.lms.backend.client.order;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class ClientOrderReleaseService {

    private static final String CLIENT_CANCELLED = "CLIENT_CANCELLED";
    private static final String PAYMENT_DEADLINE_EXPIRED = "PAYMENT_DEADLINE_EXPIRED";

    private final SharedCurrentUserReader currentUser;
    private final CustomerOrderRepository orders;
    private final CustomerOrderReleaseRepository releases;
    private final ProductRepository products;
    private final InventoryRepository inventory;

    public ClientOrderReleaseService(SharedCurrentUserReader currentUser, CustomerOrderRepository orders,
            CustomerOrderReleaseRepository releases, ProductRepository products, InventoryRepository inventory) {
        this.currentUser = currentUser;
        this.orders = orders;
        this.releases = releases;
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional
    public ClientCancelOrderResponse cancelOwnOrder(String orderReference) {
        long clientId = currentUser.sharedReadCurrentUser().userID();
        CustomerOrder order = orders.findByOrderReferenceAndClientUserID(orderReference, clientId)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "CLIENT_ORDER_NOT_FOUND",
                        "Order was not found."));
        if (!SharedOrderStatus.CANCELLED.name().equals(order.orderStatus)) {
            if (!order.isAwaitingPayment() || releases.hasPaymentOrFulfillment(order.orderID)) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_CANCELLATION_NOT_ALLOWED",
                        "Only unpaid orders awaiting payment can be cancelled.");
            }
            String reason = order.canAcceptPaymentAt(releases.databaseTime()) ? CLIENT_CANCELLED
                    : PAYMENT_DEADLINE_EXPIRED;
            release(order, clientId, reason);
        }
        return new ClientCancelOrderResponse(order.orderReference, SharedOrderStatus.CANCELLED);
    }

    @Transactional(timeout = 15)
    public void expireIfDue(long orderId) {
        CustomerOrder order = orders.findUnlockedOrderForExpiry(orderId).orElse(null);
        if (order == null || !order.isAwaitingPayment() || order.canAcceptPaymentAt(releases.databaseTime())
                || releases.hasPaymentOrFulfillment(orderId)) {
            return;
        }
        release(order, null, PAYMENT_DEADLINE_EXPIRED);
    }

    private void release(CustomerOrder order, Long actorUserId, String reason) {
        // With the existing order locked, follow Create Order's product and inventory lock order.
        List<Long> productIds = releases.findOrderProductIds(order.orderID);
        if (!productIds.isEmpty()) {
            products.findByProductIdInOrderByProductIdAsc(productIds);
            Map<Long, Inventory> balances = inventory.findByProductIdInOrderByProductIdAsc(productIds).stream()
                    .collect(Collectors.toMap(balance -> balance.productId, Function.identity()));
            for (var hold : releases.lockHeldReservations(order.orderID)) {
                Inventory balance = balances.get(hold.productId());
                if (balance == null || balance.reservedStock < hold.quantity()) {
                    throw new IllegalStateException("Inconsistent inventory for reservation " + hold.reservationId());
                }
                if (releases.markReleased(hold.reservationId()) != 1) {
                    throw new IllegalStateException("Could not release reservation " + hold.reservationId());
                }
                balance.reservedStock -= hold.quantity();
                releases.insertReleaseMovement(hold.productId(), actorUserId, hold.quantity(),
                        reason + ": " + order.orderReference);
            }
        }
        order.orderStatus = SharedOrderStatus.CANCELLED.name();
    }
}
