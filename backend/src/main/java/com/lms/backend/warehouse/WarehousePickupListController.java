package com.lms.backend.warehouse;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("delivery-full")
@RequestMapping("/api/warehouse/orders/{orderReference}/pickup-list")
class WarehousePickupListController {

    private final WarehousePickupListService lists;

    WarehousePickupListController(WarehousePickupListService lists) {
        this.lists = lists;
    }

    @GetMapping
    Map<String, Object> view(@PathVariable String orderReference) {
        return lists.activeList(orderReference);
    }

    @PostMapping
    Map<String, Object> create(@PathVariable String orderReference, @RequestBody PickupListRequest request) {
        return lists.create(orderReference, request.requestId());
    }

    @GetMapping("/{listId}/qr-code")
    ResponseEntity<byte[]> qr(@PathVariable String orderReference, @PathVariable long listId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(lists.qr(orderReference, listId));
    }

    record PickupListRequest(UUID requestId) {}
}
