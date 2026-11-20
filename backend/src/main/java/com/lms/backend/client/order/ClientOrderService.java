package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.CustomerOrderWriteRepository;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.Product;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class ClientOrderService {

    private static final Duration PAYMENT_WINDOW = Duration.ofHours(24);
    private static final BigDecimal MAX_ORDER_TOTAL = new BigDecimal("9999999999.99");
    private static final SharedOrderStatus INITIAL_STATUS = SharedOrderStatus.AWAITING_PAYMENT;

    private final SharedCurrentUserReader currentUser;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final CustomerOrderRepository orders;
    private final CustomerOrderWriteRepository orderWrites;

    public ClientOrderService(SharedCurrentUserReader currentUser, ProductRepository products,
            InventoryRepository inventory, CustomerOrderRepository orders, CustomerOrderWriteRepository orderWrites) {
        this.currentUser = currentUser;
        this.products = products;
        this.inventory = inventory;
        this.orders = orders;
        this.orderWrites = orderWrites;
    }

    @Transactional
    public ClientCreateOrderResponse createOrder(ClientCreateOrderRequest request) {
        long clientId = currentUser.sharedReadCurrentUser().userID();
        Map<Long, Integer> quantities = validateItems(request.items());
        String recipient = requiredText(request.recipientName(), "Recipient name", 120);
        String phone = requiredText(request.contactPhone(), "Contact phone", 40);
        String address = requiredText(request.deliveryAddress(), "Hong Kong delivery address", 1000);

        // Lock product rows first (price and status), then inventory rows, each in product ID order.
        List<Product> orderedProducts = products.findByProductIdInOrderByProductIdAsc(quantities.keySet());
        if (orderedProducts.size() != quantities.size()) {
            throw new SharedRuleException(HttpStatus.NOT_FOUND, "CLIENT_ORDER_PRODUCT_NOT_FOUND",
                    "One or more selected products do not exist.");
        }
        Map<Long, Inventory> balances = inventory.findByProductIdInOrderByProductIdAsc(quantities.keySet()).stream()
                .collect(Collectors.toMap(balance -> balance.productId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        for (Product product : orderedProducts) {
            if (!"ACTIVE".equals(product.listingStatus)) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_PRODUCT_INACTIVE",
                        "One or more selected products are no longer listed.");
            }
            int quantity = quantities.get(product.productId);
            Inventory balance = balances.get(product.productId);
            if (balance == null || quantity > balance.physicalStock - balance.reservedStock) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_STOCK_UNAVAILABLE",
                        "There is not enough stock for one or more selected products.");
            }
            balance.reservedStock += quantity;
            total = total.add(product.currentPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        if (total.compareTo(MAX_ORDER_TOTAL) > 0) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_TOTAL_TOO_LARGE",
                    "The order total exceeds the supported amount.");
        }

        CustomerOrder order = new CustomerOrder();
        order.orderReference = "ORD-" + UUID.randomUUID();
        order.clientUserID = clientId;
        order.orderStatus = INITIAL_STATUS.name();
        order.createdTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        order.paymentDeadline = order.createdTime.plus(PAYMENT_WINDOW);
        order.totalAmount = total;
        orders.saveAndFlush(order);

        orderWrites.insertInitialDeliveryDetail(order.orderID, recipient, phone, address);
        for (Product product : orderedProducts) {
            int quantity = quantities.get(product.productId);
            long itemId = orderWrites.insertItem(order.orderID, product.productId, quantity, product.currentPrice);
            orderWrites.insertHeldReservation(itemId, quantity);
            orderWrites.insertReservationMovement(product.productId, clientId, quantity, order.orderReference);
        }
        return new ClientCreateOrderResponse(order.orderReference, INITIAL_STATUS, total, order.paymentDeadline);
    }

    private Map<Long, Integer> validateItems(List<ClientCreateOrderRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            throw invalidOrder("Select at least one product.");
        }
        Map<Long, Integer> quantities = new HashMap<>();
        for (ClientCreateOrderRequest.Item item : items) {
            if (item == null || item.productId() == null || item.quantity() == null) {
                throw invalidOrder("Each item needs a product ID and a quantity.");
            }
            long productId;
            int quantity;
            try {
                productId = item.productId().longValueExact();
                quantity = item.quantity().intValueExact();
            } catch (ArithmeticException ex) {
                throw invalidOrder("Use whole numbers within range for product IDs and quantities.");
            }
            if (productId <= 0 || quantity <= 0) {
                throw invalidOrder("Product IDs and quantities must be positive.");
            }
            if (quantities.putIfAbsent(productId, quantity) != null) {
                throw new SharedRuleException(HttpStatus.BAD_REQUEST, "CLIENT_ORDER_DUPLICATE_PRODUCT",
                        "Use each product ID only once.");
            }
        }
        return quantities;
    }

    private String requiredText(String input, String label, int maxLength) {
        if (input == null || input.isBlank()) {
            throw invalidOrder(label + " is required.");
        }
        String value = input.strip();
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw invalidOrder(label + " is too long.");
        }
        if (value.codePoints().anyMatch(codePoint -> codePoint == 0
                || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE))) {
            throw invalidOrder(label + " contains invalid text.");
        }
        return value;
    }

    private SharedRuleException invalidOrder(String message) {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "CLIENT_ORDER_INVALID", message);
    }
}
