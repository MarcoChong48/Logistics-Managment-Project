package com.lms.backend.client.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientOrderController {

    private final ClientOrderService orders;
    private final ClientOrderReleaseService releases;

    public ClientOrderController(ClientOrderService orders, ClientOrderReleaseService releases) {
        this.orders = orders;
        this.releases = releases;
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
}
