package com.lms.backend.client.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientOrderController {

    private final ClientOrderService orders;

    public ClientOrderController(ClientOrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/api/client/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientCreateOrderResponse createOrder(@RequestBody ClientCreateOrderRequest request) {
        return orders.createOrder(request);
    }
}
