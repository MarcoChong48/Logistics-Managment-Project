package com.lms.backend.client.order;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository.CheckoutItem;
import com.lms.backend.shared.database.CustomerOrderWriteRepository;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.error.SharedRuleException;

@Component
class ClientOrderReservations {

    private final CustomerOrderReleaseRepository releases;
    private final CustomerOrderWriteRepository writes;
    private final ProductRepository products;
    private final InventoryRepository inventory;

    ClientOrderReservations(CustomerOrderReleaseRepository releases, CustomerOrderWriteRepository writes,
            ProductRepository products, InventoryRepository inventory) {
        this.releases = releases;
        this.writes = writes;
        this.products = products;
        this.inventory = inventory;
    }

    Map<Long, Inventory> lockAvailableStock(List<CheckoutItem> items) {
        var balances = lockStock(items.stream().map(CheckoutItem::productId).toList());
        for (var item : items) {
            Inventory balance = balances.get(item.productId());
            if (balance == null || item.quantity() > balance.physicalStock - balance.reservedStock) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_STOCK_UNAVAILABLE",
                        "There is not enough stock for one or more selected products.");
            }
        }
        return balances;
    }

    void reserve(CustomerOrder order, long paymentId, List<CheckoutItem> items, Map<Long, Inventory> balances) {
        for (var item : items) {
            balances.get(item.productId()).reservedStock += item.quantity();
            writes.insertHeldReservation(item.orderItemId(), paymentId, item.quantity());
            writes.insertReservationMovement(item.productId(), order.clientUserID, item.quantity(),
                    order.orderReference);
        }
    }

    void release(CustomerOrder order, Long paymentId, Long actorUserId, String reason) {
        List<Long> productIds = releases.findHeldProductIds(order.orderID, paymentId);
        if (productIds.isEmpty()) {
            return;
        }
        var balances = lockStock(productIds);
        for (var hold : releases.lockHeldReservations(order.orderID, paymentId)) {
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

    // The parent order is already locked. Use the same sorted product and inventory locks for holds and releases.
    private Map<Long, Inventory> lockStock(List<Long> productIds) {
        products.findByProductIdInOrderByProductIdAsc(productIds);
        return inventory.findByProductIdInOrderByProductIdAsc(productIds).stream()
                .collect(Collectors.toMap(balance -> balance.productId, Function.identity()));
    }
}
