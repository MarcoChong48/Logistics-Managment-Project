package com.lms.backend.client.order;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.client.stripe.StripeSandboxCheckout;

@RestController
public class ClientOrderController {

    private final ClientOrderService orders;
    private final ClientOrderReleaseService releases;
    private final ClientCheckoutService checkout;
    private final ObjectProvider<StripeSandboxCheckout> stripe;

    public ClientOrderController(ClientOrderService orders, ClientOrderReleaseService releases,
            ClientCheckoutService checkout, ObjectProvider<StripeSandboxCheckout> stripe) {
        this.orders = orders;
        this.releases = releases;
        this.checkout = checkout;
        this.stripe = stripe;
    }

    @PostMapping("/api/client/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientCreateOrderResponse createOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ClientCreateOrderRequest request) {
        return orders.createOrder(idempotencyKey, request);
    }

    @PostMapping("/api/client/orders/{orderReference}/cancel")
    public ClientCancelOrderResponse cancelOrder(@PathVariable("orderReference") String orderReference) {
        return releases.cancelOwnOrder(orderReference);
    }

    @PostMapping("/api/client/orders/{orderReference}/checkout")
    public ClientCheckoutResponse startCheckout(@PathVariable("orderReference") String orderReference) {
        var provider = stripe.getIfAvailable();
        return provider == null ? checkout.startCheckout(orderReference, amount -> { })
                : provider.start(orderReference);
    }
}
