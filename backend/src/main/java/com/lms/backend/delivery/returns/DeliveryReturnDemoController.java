package com.lms.backend.delivery.returns;

import static com.lms.backend.delivery.support.DeliveryValues.id;

import com.lms.backend.shared.login.SharedCurrentUserReader;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("delivery-full & admintest")
@ConditionalOnProperty(name = "lms.delivery.demo-enabled", havingValue = "true")
@RequestMapping("/api/delivery/demo/packages")
class DeliveryReturnDemoController {
    private final DeliveryReturnDemoService simulation;
    private final SharedCurrentUserReader currentUser;

    DeliveryReturnDemoController(DeliveryReturnDemoService simulation, SharedCurrentUserReader currentUser) {
        this.simulation = simulation;
        this.currentUser = currentUser;
    }

    @PostMapping("/{id}/confirm-return")
    Map<String,Object> receive(@PathVariable("id") String packageId, @RequestBody Map<String,Object> body) {
        return simulation.confirmReceipt(currentUser.sharedReadCurrentUser().userID(), id(packageId), body);
    }

    @PostMapping("/{id}/client-instruction")
    Map<String,Object> instruction(@PathVariable("id") String packageId, @RequestBody Map<String,Object> body) {
        return simulation.clientInstruction(currentUser.sharedReadCurrentUser().userID(), id(packageId), body);
    }

    @PostMapping("/{id}/admin-decision")
    Map<String,Object> decide(@PathVariable("id") String packageId, @RequestBody Map<String,Object> body) {
        return simulation.adminDecision(currentUser.sharedReadCurrentUser().userID(), id(packageId), body);
    }
}
