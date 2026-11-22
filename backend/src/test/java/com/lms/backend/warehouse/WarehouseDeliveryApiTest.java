package com.lms.backend.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

@ActiveProfiles("delivery-full")
class WarehouseDeliveryApiTest extends WarehouseApiTest {

    @Test
    void pickupListRequiresCommittedPacking() throws Exception {
        var order = order(1);
        var warehouse = signIn(user("WAREHOUSE_WORKER"));
        address(order);
        String list = "/api/warehouse/orders/" + order.reference() + "/pickup-list";
        write(list, warehouse, request(UUID.randomUUID())).andExpect(status().isConflict());
        assertStock(order, 10, 1);
    }

    @Test
    void separatePickupListIsReplaySafeAndDeliveryConsumesOnce() throws Exception {
        var order = order(2);
        var warehouse = signIn(user("WAREHOUSE_WORKER"));
        address(order);
        String base = "/api/warehouse/orders/" + order.reference();
        String list = base + "/pickup-list";
        String body = request(UUID.randomUUID());
        scan(order, warehouse, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        scan(order, warehouse, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        write(taskPath(order) + "/confirm-packing", warehouse, null).andExpect(status().isOk());
        TestTransaction.flagForCommit();
        TestTransaction.end();

        try {
            TestTransaction.start();
            verifyHandoffAndCollection(order, warehouse, base, list, body);
        } finally {
            if (TestTransaction.isActive()) {
                TestTransaction.end();
            }
            new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> deleteRaceFixture(order, warehouse.userId()));
        }
    }

    private void verifyHandoffAndCollection(FixtureOrder order, Session warehouse, String base, String list,
            String body) throws Exception {
        var driver = signIn(user("DELIVERY_WORKER"));
        var client = signIn(order.client());
        var admin = signIn(user("ADMIN"));
        read(base + "/package/qr-code", warehouse).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        read(list, warehouse).andExpect(status().isOk()).andExpect(jsonPath("$.pickupListId").doesNotExist());
        assertStock(order, 10, 2);
        write(list, client, body).andExpect(status().isForbidden());
        db.sql("UPDATE payment SET amount = 0 WHERE payment_id = ?").param(order.paymentId()).update();
        write(list, warehouse, body).andExpect(status().isConflict());
        read(list, warehouse).andExpect(status().isOk()).andExpect(jsonPath("$.pickupListId").doesNotExist());
        db.sql("UPDATE payment SET amount = 10 WHERE payment_id = ?").param(order.paymentId()).update();

        var created = write(list, warehouse, body).andExpect(status().isOk()).andReturn();
        var data = JsonPath.parse(created.getResponse().getContentAsString());
        String listId = data.read("$.pickupListId");
        String listQr = data.read("$.listQrCode");
        String packageQr = order.reference() + "-PACKAGE";
        assertNotEquals(packageQr, listQr);
        assertNotEquals(order.reference(), listQr);
        assertNotEquals(order.sku(), packageQr);
        read(base + "/package", warehouse).andExpect(status().isOk())
                .andExpect(jsonPath("$.packageQrCode").value(packageQr));
        write(list, warehouse, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupListId").value(listId));
        write(list, warehouse, request(UUID.randomUUID())).andExpect(status().isConflict());
        assertEquals(1L, db.sql("""
                SELECT count(*) FROM pickup_list_item i JOIN delivery_package p USING (package_id)
                WHERE p.order_id = ?
                """).param(order.id()).query(Long.class).single());
        read(list + "/" + listId + "/qr-code", warehouse).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        read("/api/delivery/workspace", driver).andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.pickup").value(true))
                .andExpect(jsonPath("$.capabilities.attempts").value(true))
                .andExpect(jsonPath("$.capabilities.returns").value(true))
                .andExpect(jsonPath("$.capabilities.proofs").value(true));
        write("/api/delivery/pickup-lists/claim", driver, """
                {"requestId":"%s","qrCode":"%s"}
                """.formatted(UUID.randomUUID(), listQr)).andExpect(status().isOk());
        assertStock(order, 10, 2);
        String collect = "/api/delivery/pickup-lists/" + listId + "/collect";
        String collection = """
                {"requestId":"%s","packageQrCode":"%s"}
                """.formatted(UUID.randomUUID(), packageQr);
        db.sql("UPDATE payment SET amount = 0 WHERE payment_id = ?").param(order.paymentId()).update();
        write(collect, driver, collection).andExpect(status().isConflict());
        assertStock(order, 10, 2);
        db.sql("UPDATE payment SET amount = 10 WHERE payment_id = ?").param(order.paymentId()).update();
        write(collect, driver, collection).andExpect(status().isOk());
        write(collect, driver, collection).andExpect(status().isOk());
        assertStock(order, 8, 0);
        assertTrue(db.sql("""
                SELECT status = 'CONSUMED' AND consumed_at IS NOT NULL FROM stock_reservation WHERE payment_id = ?
                """).param(order.paymentId()).query(Boolean.class).single());
        assertEquals(1L, db.sql("""
                SELECT count(*) FROM stock_movement
                WHERE product_id = ? AND movement_type = 'PICKUP' AND quantity_delta = -2 AND reserved_delta = -2
                """).param(order.productId()).query(Long.class).single());
        read("/api/client/orders/" + order.reference() + "/tracking", client).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("SHIPPED"));
        read("/api/admin/orders/" + order.id(), admin).andExpect(status().isOk());
    }

    private void address(FixtureOrder order) {
        db.sql("""
                INSERT INTO order_delivery_detail (order_id, version_no, recipient_name, phone, address_text)
                VALUES (?, 1, 'Test recipient', '00000000', 'Test delivery address')
                """).param(order.id()).update();
    }

    private static String request(UUID id) {
        return """
                {"requestId":"%s"}
                """.formatted(id);
    }

    private void assertStock(FixtureOrder order, int physical, int reserved) {
        assertTrue(db.sql("SELECT physical_stock = ? AND reserved_stock = ? FROM inventory WHERE product_id = ?")
                .param(physical).param(reserved).param(order.productId()).query(Boolean.class).single());
    }
}
