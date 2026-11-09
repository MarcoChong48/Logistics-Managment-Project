package hk.edu.hkmu.lms.delivery.returns;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class ReturnController {
    private final PackageReturns service;

    ReturnController(PackageReturns service) {
        this.service = service;
    }

    @PostMapping("/packages/{id}/return")
    Map<String, Object> returned(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.report(auth.getName(), id, body);
    }

    @PostMapping("/demo/packages/{id}/confirm-return")
    Map<String, Object> confirm(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.confirmDemo(auth.getName(), id, body);
    }

    @PostMapping("/demo/packages/{id}/client-instruction")
    Map<String, Object> instruction(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.instructDemo(auth.getName(), id, body);
    }

    @PostMapping("/demo/packages/{id}/admin-decision")
    Map<String, Object> decision(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.decideDemo(auth.getName(), id, body);
    }
}
