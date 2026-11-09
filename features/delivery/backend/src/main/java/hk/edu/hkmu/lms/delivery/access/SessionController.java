package hk.edu.hkmu.lms.delivery.access;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class SessionController {
    private final DeliveryAccess service;

    SessionController(DeliveryAccess service) {
        this.service = service;
    }

    @GetMapping("/session")
    Map<String, Object> session(Authentication auth, CsrfToken csrf) {
        boolean signedIn=auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
        Map<String, Object> result=new LinkedHashMap<>();
        result.put("authenticated",signedIn);
        result.put("user",signedIn?service.user(auth.getName()):null);
        result.put("csrfToken",csrf.getToken()); result.put("csrfHeader",csrf.getHeaderName()); result.put("mode",service.mode());
        return result;
    }
}
