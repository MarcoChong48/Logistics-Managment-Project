package hk.edu.hkmu.lms.delivery.pickup;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class PickupController {
    private final PickupOperations service;

    PickupController(PickupOperations service) {
        this.service = service;
    }

    @PostMapping("/pickup-lists/claim")
    Map<String, Object> claim(Authentication auth, @RequestBody Map<String, Object> body) {
        return service.claim(auth.getName(), body);
    }

    @PostMapping("/pickup-lists/{id}/collect")
    Map<String, Object> collect(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.collect(auth.getName(), id, body);
    }

    @PostMapping("/pickup-lists/{id}/cannot-collect")
    Map<String, Object> cannot(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.cannotCollect(auth.getName(), id, body);
    }

    @PostMapping("/pickup-lists/{id}/close")
    Map<String, Object> close(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.close(auth.getName(), id, body);
    }

    @PostMapping("/demo/pickup-lists/{id}/release")
    Map<String, Object> release(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.releaseDemo(auth.getName(), id, body);
    }
}
