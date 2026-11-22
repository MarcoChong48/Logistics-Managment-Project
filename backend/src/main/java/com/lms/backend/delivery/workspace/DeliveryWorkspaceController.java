package com.lms.backend.delivery.workspace;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.login.SharedCurrentUserReader;

@RestController
public class DeliveryWorkspaceController {

    private final SharedCurrentUserReader users;
    private final DeliveryWorkspaceService workspace;

    DeliveryWorkspaceController(SharedCurrentUserReader users, DeliveryWorkspaceService workspace) {
        this.users = users;
        this.workspace = workspace;
    }

    @GetMapping("/api/delivery/workspace")
    public Map<String, Object> workspace() {
        return workspace.readWorkspace(users.sharedReadCurrentUser());
    }
}
