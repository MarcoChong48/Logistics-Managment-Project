package com.lms.backend.client.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lms.backend.client.order.ClientCheckoutResponse;
import com.lms.backend.client.order.ClientOrderReleaseService;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.value.SharedUserRole;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;

@SpringBootTest(properties = {
        "lms.client.stripe.enabled=true", "lms.client.stripe.reconciliation-interval=PT1H",
        "lms.client.orders.expiry.enabled=false", "lms.shared.bootstrap-admin-username=",
        "LMS_STRIPE_TEST_API_KEY=sk_test_fixture", "LMS_STRIPE_WEBHOOK_SECRET=whsec_fixture",
        "LMS_STRIPE_RETURN_URL=http://localhost:8081/shared/shared-welcome.html",
        "LMS_STRIPE_CHECKOUT_SCOPE=stripe-tests"
})
@AutoConfigureMockMvc
class StripeSandboxCheckoutTest {

    @Autowired private MockMvc mvc;
    @Autowired private StripeSandboxCheckout checkout;
    @Autowired private ClientOrderReleaseService releases;
    @Autowired private JdbcClient db;
    @MockitoSpyBean private StripeSandboxGateway gateway;

    private final String reference = "STRIPE-" + UUID.randomUUID();
    private long owner;
    private long product;
    private long order;

    @BeforeEach
    void createFixtures() {
        owner = db.sql("""
                INSERT INTO app_user (username, password_hash, role) VALUES (:ref, 'unused', 'CLIENT') RETURNING user_id
                """).param("ref", reference).query(Long.class).single();
        product = db.sql("""
                INSERT INTO product (sku, name, current_price) VALUES (:ref, 'Fixture', 12.34) RETURNING product_id
                """).param("ref", reference).query(Long.class).single();
        db.sql("INSERT INTO inventory (product_id, physical_stock) VALUES (:id, 5)").param("id", product).update();
        order = db.sql("""
                INSERT INTO customer_order (order_reference, client_user_id, total_amount, payment_deadline)
                VALUES (:ref, :owner, 12.34, NOW() + INTERVAL '24 hours') RETURNING order_id
                """).param("ref", reference).param("owner", owner).query(Long.class).single();
        db.sql("INSERT INTO order_item (order_id, product_id, quantity, unit_price) VALUES (:id, :product, 1, 12.34)")
                .param("id", order).param("product", product).update();
    }

    @AfterEach
    void cleanFixtures() {
        db.sql("DELETE FROM notification WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM client_stripe_event WHERE payment_id IN (SELECT payment_id FROM payment WHERE order_id=:id)")
                .param("id", order).update();
        db.sql("DELETE FROM client_stripe_checkout WHERE payment_id IN (SELECT payment_id FROM payment WHERE order_id=:id)")
                .param("id", order).update();
        db.sql("DELETE FROM stock_reservation WHERE order_item_id IN (SELECT order_item_id FROM order_item WHERE order_id=:id)")
                .param("id", order).update();
        db.sql("DELETE FROM refund WHERE payment_id IN (SELECT payment_id FROM payment WHERE order_id=:id)")
                .param("id", order).update();
        db.sql("DELETE FROM payment WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM order_item WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM customer_order WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM stock_movement WHERE product_id=:id").param("id", product).update();
        db.sql("DELETE FROM inventory WHERE product_id=:id").param("id", product).update();
        db.sql("DELETE FROM product WHERE product_id=:id").param("id", product).update();
        db.sql("DELETE FROM app_user WHERE user_id=:id").param("id", owner).update();
    }

    @Test
    void concurrentRetriesReuseOneProviderCreationAndOneHold() throws Exception {
        var remoteStarted = new CountDownLatch(1);
        var mayReturn = new CountDownLatch(1);
        doAnswer(call -> {
            assertEquals(new BigDecimal("12.34"), call.getArgument(1));
            remoteStarted.countDown();
            assertTrue(mayReturn.await(5, TimeUnit.SECONDS));
            return session(call.getArgument(0), "open", "unpaid");
        }).when(gateway).create(anyLong(), any());

        ClientCheckoutResponse first;
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(this::start);
            try {
                assertTrue(remoteStarted.await(5, TimeUnit.SECONDS));
                assertFalse(start().providerCheckoutAvailable());
            } finally {
                mayReturn.countDown();
            }
            first = result.get(10, TimeUnit.SECONDS);
        }
        doReturn(session(first.checkoutId(), "open", "unpaid")).when(gateway).retrieve(sessionId(first.checkoutId()));
        assertTrue(first.providerCheckoutAvailable());
        assertEquals(first, start());
        verify(gateway, times(1)).create(anyLong(), any());
        assertEquals(1, reservedStock());
    }

    @Test
    void ambiguousCreateRemainsSingleAndSignedEvidenceCanBindAndRetry() throws Exception {
        doThrow(new ApiConnectionException("Fixture timeout")).when(gateway).create(anyLong(), any());
        var first = start();
        assertFalse(first.providerCheckoutAvailable());
        assertNull(first.checkoutUrl());
        assertEquals(first, start());
        verify(gateway, times(1)).create(anyLong(), any());
        long id = first.checkoutId();
        doThrow(new ApiConnectionException("Fixture timeout")).doReturn(session(id, "expired", "unpaid"))
                .when(gateway).retrieve(sessionId(id));

        String event = event(id, "evt_retry" + id, "checkout.session.expired");
        send(event, sign(event));
        assertEquals(1, reservedStock());
        checkout.sweep();
        send(event, sign(event));
        assertEquals(sessionId(id), db.sql("SELECT session_id FROM client_stripe_checkout WHERE payment_id=:id")
                .param("id", id).query(String.class).single());
        assertEquals(1L, db.sql("SELECT count(*) FROM client_stripe_event WHERE payment_id=:id AND processed_at IS NOT NULL")
                .param("id", id).query(Long.class).single());
        verify(gateway, times(2)).retrieve(sessionId(id));
        assertEquals(0, reservedStock());
        assertEquals("AWAITING_PAYMENT", orderStatus());
    }

    @Test
    void checkoutCutoffStopsEntryButCaptureBeforeOrderDeadlineStillAccepts() throws Exception {
        long id = startOpenSession();
        passEntryDeadline(id);
        assertThrows(SharedRuleException.class, this::start);
        doReturn(captured(id)).when(gateway).retrieve(sessionId(id));
        String expiredEvent = event(id, "evt_old" + id, "checkout.session.expired");
        send(expiredEvent, sign(expiredEvent));
        var acceptedAt = db.sql("SELECT accepted_at FROM payment WHERE payment_id=:id")
                .param("id", id).query(OffsetDateTime.class).single();
        assertTrue(acceptedAt.toInstant().isAfter(db.sql("SELECT checkout_expires_at FROM payment WHERE payment_id=:id")
                .param("id", id).query(OffsetDateTime.class).single().toInstant()));
        String completedEvent = event(id, "evt_paid" + id, "checkout.session.completed");
        send(completedEvent, sign(completedEvent));
        assertEquals(acceptedAt, db.sql("SELECT accepted_at FROM payment WHERE payment_id=:id")
                .param("id", id).query(OffsetDateTime.class).single());
        assertEquals("READY_FOR_PICKING", orderStatus());
        assertEquals(1, reservedStock());
        assertEquals(0L, refundCount(id));
    }

    @Test
    void bestEffortExpiryOfOpenSessionReleasesOnlyAfterVerifiedUnpaidClosure() throws Exception {
        long id = startOpenSession();
        passEntryDeadline(id);
        doReturn(session(id, "open", "unpaid"), session(id, "expired", "unpaid"))
                .when(gateway).retrieve(sessionId(id));
        doNothing().when(gateway).expire(sessionId(id));
        checkout.sweep();
        verify(gateway).expire(sessionId(id));
        assertEquals(0, reservedStock());
        assertEquals("AWAITING_PAYMENT", orderStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void captureAfterStrictOrderDeadlineOrCancellationCreatesOnlyARequestedRefund(boolean cancel) throws Exception {
        long id = startOpenSession();
        if (cancel) {
            asOwner(() -> releases.cancelOwnOrder(reference));
        } else {
            makeOrderOverdue();
            releases.expireIfDue(order);
        }
        doReturn(captured(id)).when(gateway).retrieve(sessionId(id));
        String paid = event(id, "evt_late" + id, "checkout.session.completed");
        send(paid, sign(paid));
        send(paid, sign(paid));
        assertEquals("CANCELLED", orderStatus());
        assertEquals(0, reservedStock());
        assertEquals(1L, refundCount(id));
        assertTrue(db.sql("""
                SELECT refund_status='REQUESTED' AND amount=12.34 AND completed_at IS NULL AND provider_reference IS NULL
                FROM refund WHERE payment_id=:id
                """).param("id", id).query(Boolean.class).single());
    }

    @Test
    void signedPaidEventWithNoCaptureEvidenceCannotRefundAStatusOnlyPayment() throws Exception {
        long id = startOpenSession();
        db.sql("UPDATE payment SET payment_status='ACCEPTED' WHERE payment_id=:id")
                .param("id", id).update();
        asOwner(() -> releases.cancelOwnOrder(reference));

        doReturn(session(id, "complete", "paid")).when(gateway).retrieve(sessionId(id));
        String paid = event(id, "evt_missing_capture" + id, "checkout.session.completed");
        send(paid, sign(paid));
        assertEquals(0L, refundCount(id));
        assertEquals("RECONCILIATION_REQUIRED", db.sql("SELECT payment_status FROM payment WHERE payment_id=:id")
                .param("id", id).query(String.class).single());

        doReturn(captured(id)).when(gateway).retrieve(sessionId(id));
        checkout.sweep();
        String replay = event(id, "evt_verified_replay" + id, "checkout.session.completed");
        send(replay, sign(replay));
        send(replay, sign(replay));

        verify(gateway, never()).expire(sessionId(id));
        assertEquals("CANCELLED", orderStatus());
        assertEquals(0, reservedStock());
        assertEquals(1L, refundCount(id));
        assertTrue(db.sql("""
                SELECT refund_status='REQUESTED' AND amount=12.34 AND completed_at IS NULL
                    AND provider_reference IS NULL
                FROM refund WHERE payment_id=:id
                """).param("id", id).query(Boolean.class).single());
    }

    @Test
    void staleUnmatchedClaimNeverCreatesAgainAndOrderExpiryStillReleases() throws Exception {
        doThrow(new ApiConnectionException("Fixture timeout")).when(gateway).create(anyLong(), any());
        var first = start();
        checkout.sweep();
        assertEquals(first, start());
        assertEquals(1, reservedStock());
        assertTrue(db.sql("SELECT session_id IS NULL FROM client_stripe_checkout WHERE payment_id=:id")
                .param("id", first.checkoutId()).query(Boolean.class).single());

        makeOrderOverdue();
        releases.expireIfDue(order);
        checkout.sweep();
        assertThrows(SharedRuleException.class, this::start);
        verify(gateway, times(1)).create(anyLong(), any());
        verify(gateway, never()).retrieve(any());
        assertEquals("CANCELLED", orderStatus());
        assertEquals(0, reservedStock());
        assertEquals(0L, refundCount(first.checkoutId()));
    }

    @Test
    void mismatchedCaptureNeverAcceptsOrderAndQueuesActualCapturedAmount() throws Exception {
        long id = startOpenSession();
        var result = captured(id);
        result.getPaymentIntentObject().setAmountReceived(1500L);
        doReturn(result).when(gateway).retrieve(sessionId(id));
        String paid = event(id, "evt_wrong_amount" + id, "checkout.session.completed");
        send(paid, sign(paid));
        send(paid, sign(paid));

        assertEquals("AWAITING_PAYMENT", orderStatus());
        assertEquals(1L, refundCount(id));
        assertTrue(db.sql("""
                SELECT refund_status='REQUESTED' AND amount=15.00 AND completed_at IS NULL
                FROM refund WHERE payment_id=:id
                """).param("id", id).query(Boolean.class).single());
    }

    @Test
    void onlySignatureVerifiedPostCanReachWebhook() throws Exception {
        long id = startOpenSession();
        String path = StripeSandboxConfiguration.WEBHOOK;
        String event = event(id, "evt_invalid" + id, "checkout.session.completed");
        mvc.perform(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(event + " ")
                .header("Stripe-Signature", sign(event))).andExpect(status().isBadRequest());
        mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
        verify(gateway, never()).retrieve(any());
        assertEquals("AWAITING_PAYMENT", orderStatus());
    }

    @Test
    void invalidUtf8CannotReuseSignatureForDecodedReplacementCharacter() throws Exception {
        long id = startOpenSession();
        String raw = event(id, "evt_invalid_utf8" + id, "checkout.session.completed")
                .replace("\"livemode\":false", "\"livemode\":false,\"note\":\"\uFFFD\"");
        byte[] malformed = raw.replace('\uFFFD', '~').getBytes(StandardCharsets.UTF_8);
        malformed[raw.indexOf('\uFFFD')] = (byte) 0xff;
        String path = StripeSandboxConfiguration.WEBHOOK;
        mvc.perform(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(malformed)
                .header("Stripe-Signature", sign(raw))).andExpect(status().isBadRequest());
        verify(gateway, never()).retrieve(any());
        assertEquals("AWAITING_PAYMENT", orderStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "3.99", "1000000.00"})
    void unsupportedSavedAmountsFailBeforeProviderAndStock(String amount) throws Exception {
        db.sql("UPDATE customer_order SET total_amount=:amount WHERE order_id=:id")
                .param("amount", new BigDecimal(amount)).param("id", order).update();
        var failure = assertThrows(SharedRuleException.class, this::start);
        assertEquals("CLIENT_STRIPE_AMOUNT_UNSUPPORTED", failure.getCode());
        verify(gateway, never()).create(anyLong(), any());
        assertEquals(0, reservedStock());
    }

    private long startOpenSession() throws Exception {
        doAnswer(call -> session(call.getArgument(0), "open", "unpaid")).when(gateway).create(anyLong(), any());
        var started = start();
        assertTrue(started.providerCheckoutAvailable());
        return started.checkoutId();
    }

    private ClientCheckoutResponse start() {
        return asOwner(() -> checkout.start(reference));
    }

    private <T> T asOwner(java.util.function.Supplier<T> action) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new SharedCurrentUser(owner, reference, SharedUserRole.CLIENT), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void passEntryDeadline(long checkoutId) {
        db.sql("""
                UPDATE payment SET created_at=NOW()-INTERVAL '31 minutes', checkout_expires_at=NOW()-INTERVAL '1 minute'
                WHERE payment_id=:id
                """).param("id", checkoutId).update();
    }

    private void makeOrderOverdue() {
        db.sql("""
                UPDATE customer_order SET created_at=NOW()-INTERVAL '25 hours', payment_deadline=NOW()-INTERVAL '1 hour'
                WHERE order_id=:id
                """).param("id", order).update();
    }

    private String sessionId(long id) {
        return "cs_test_fixture" + id;
    }

    private Session session(long id, String status, String paymentStatus) {
        var session = new Session();
        session.setId(sessionId(id));
        session.setLivemode(false);
        session.setMode("payment");
        session.setCurrency("hkd");
        session.setClientReferenceId(Long.toString(id));
        session.setMetadata(Map.of("lms_checkout_id", Long.toString(id), "lms_checkout_scope", "stripe-tests"));
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);
        session.setAmountTotal(1234L);
        session.setExpiresAt(Instant.now().plusSeconds(1800).getEpochSecond());
        session.setUrl("https://checkout.stripe.com/c/pay/" + sessionId(id));
        return session;
    }

    private Session captured(long id) {
        var session = session(id, "complete", "paid");
        var intent = new PaymentIntent();
        intent.setId("pi_fixture" + id);
        intent.setLivemode(false);
        intent.setCurrency("hkd");
        intent.setStatus("succeeded");
        intent.setAmountReceived(1234L);
        session.setPaymentIntentObject(intent);
        return session;
    }

    private String event(long id, String eventId, String type) {
        return """
                {"object":"event","id":"%s","type":"%s","livemode":false,"data":{"object":
                {"id":"%s","object":"checkout.session","metadata":{"lms_checkout_id":"%d",
                "lms_checkout_scope":"stripe-tests"}}}}
                """.formatted(eventId, type, sessionId(id), id);
    }

    private String sign(String content) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        var hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec("whsec_fixture".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                hmac.doFinal((timestamp + "." + content).getBytes(StandardCharsets.UTF_8)));
    }

    private void send(String event, String signature) throws Exception {
        String path = StripeSandboxConfiguration.WEBHOOK;
        mvc.perform(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(event)
                .header("Stripe-Signature", signature)).andExpect(status().isNoContent());
    }

    private long refundCount(long id) {
        return db.sql("SELECT count(*) FROM refund WHERE payment_id=:id").param("id", id).query(Long.class).single();
    }

    private String orderStatus() {
        return db.sql("SELECT order_status FROM customer_order WHERE order_id=:id")
                .param("id", order).query(String.class).single();
    }

    private int reservedStock() {
        return db.sql("SELECT reserved_stock FROM inventory WHERE product_id=:id")
                .param("id", product).query(Integer.class).single();
    }
}
