package com.lms.backend.client.order;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ClientOrderReadController {

    private final ClientOrderReadService orders;

    ClientOrderReadController(ClientOrderReadService orders) {
        this.orders = orders;
    }

    @GetMapping("/api/client/orders")
    ResponseEntity<ClientOrderListResponse> list(@RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.list(limit, cursor));
    }

    @GetMapping("/api/client/orders/{orderReference}")
    ResponseEntity<ClientOrderDetailsResponse> details(@PathVariable("orderReference") String reference) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.details(reference));
    }

    @GetMapping("/api/client/orders/{orderReference}/tracking")
    ResponseEntity<ClientOrderTrackingResponse> tracking(@PathVariable("orderReference") String reference) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.tracking(reference));
    }
}
