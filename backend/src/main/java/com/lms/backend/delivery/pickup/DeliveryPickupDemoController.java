package com.lms.backend.delivery.pickup;

import static com.lms.backend.delivery.support.DeliveryValues.id;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.login.SharedCurrentUserReader;

@RestController
@Profile("delivery-full & admintest")
@ConditionalOnProperty(name = "lms.delivery.demo-enabled", havingValue = "true")
public class DeliveryPickupDemoController {
    private final SharedCurrentUserReader users;
    private final DeliveryPickupService pickup;

    DeliveryPickupDemoController(SharedCurrentUserReader users, DeliveryPickupService pickup) {
        this.users = users;
        this.pickup = pickup;
    }

    @PostMapping("/api/delivery/demo/pickup-lists/{listId}/release")
    public Map<String, Object> release(@PathVariable String listId, @RequestBody Map<String, Object> body) {
        return pickup.releaseDemo(DeliveryPickupController.workerId(users), id(listId), body);
    }
}
