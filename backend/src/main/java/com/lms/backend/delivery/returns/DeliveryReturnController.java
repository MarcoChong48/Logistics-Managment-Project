package com.lms.backend.delivery.returns;

import static com.lms.backend.delivery.support.DeliveryValues.id;

import com.lms.backend.shared.login.SharedCurrentUserReader;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("delivery-full")
@RequestMapping("/api/delivery/packages")
class DeliveryReturnController {
    private final DeliveryReturnService returns;
    private final SharedCurrentUserReader currentUser;

    DeliveryReturnController(DeliveryReturnService returns, SharedCurrentUserReader currentUser) {
        this.returns = returns;
        this.currentUser = currentUser;
    }

    @PostMapping("/{id}/return")
    Map<String,Object> report(@PathVariable("id") String packageId, @RequestBody Map<String,Object> body) {
        return returns.report(currentUser.sharedReadCurrentUser().userID(), id(packageId), body);
    }
}
