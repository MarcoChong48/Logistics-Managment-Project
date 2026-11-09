package hk.edu.hkmu.lms.delivery.workspace;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class WorkspaceController {
    private final DeliveryWorkspace service;

    WorkspaceController(DeliveryWorkspace service) {
        this.service = service;
    }

    @GetMapping("/delivery/workspace")
    Map<String, Object> workspace(Authentication auth) {
        return service.workspace(auth.getName());
    }
}
