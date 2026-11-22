package com.lms.backend.delivery.pickup;

import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.id;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedUserRole;

@RestController
@Profile("delivery-full")
@RequestMapping("/api/delivery/pickup-lists")
public class DeliveryPickupController {
    private final SharedCurrentUserReader users;
    private final DeliveryPickupService pickup;

    DeliveryPickupController(SharedCurrentUserReader users, DeliveryPickupService pickup) {
        this.users = users;
        this.pickup = pickup;
    }

    @PostMapping("/claim")
    public Map<String, Object> claim(@RequestBody Map<String, Object> body) {
        return pickup.claim(workerId(users), body);
    }

    @PostMapping("/{listId}/collect")
    public Map<String, Object> collect(@PathVariable String listId, @RequestBody Map<String, Object> body) {
        return pickup.collect(workerId(users), id(listId), body);
    }

    @PostMapping("/{listId}/cannot-collect")
    public Map<String, Object> cannotCollect(@PathVariable String listId, @RequestBody Map<String, Object> body) {
        return pickup.cannotCollect(workerId(users), id(listId), body);
    }

    @PostMapping("/{listId}/close")
    public Map<String, Object> close(@PathVariable String listId, @RequestBody Map<String, Object> body) {
        return pickup.close(workerId(users), id(listId), body);
    }

    static long workerId(SharedCurrentUserReader users) {
        var user = users.sharedReadCurrentUser();
        if (user.role() != SharedUserRole.DELIVERY_WORKER) throw denied();
        return user.userID();
    }
}
