package com.lms.backend.delivery.attempts;

import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.id;

import java.util.Map;

import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedUserRole;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("delivery-full")
@RequestMapping("/api/delivery")
class DeliveryAttemptController {
    private final DeliveryAttemptService attempts;
    private final SharedCurrentUserReader users;

    DeliveryAttemptController(DeliveryAttemptService attempts, SharedCurrentUserReader users) {
        this.attempts = attempts;
        this.users = users;
    }

    @PostMapping("/packages/{id}/start")
    Map<String, Object> start(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return attempts.start(workerId(), id(id), body);
    }

    @PostMapping("/packages/{id}/outcome")
    Map<String, Object> outcome(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return attempts.outcome(workerId(), id(id), body);
    }

    @PostMapping(value = "/packages/{id}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> upload(
            @PathVariable String id,
            @RequestParam String type,
            @RequestParam MultipartFile file,
            @RequestParam String requestId
    ) {
        return attempts.upload(workerId(), id(id), type, file, requestId);
    }

    @GetMapping("/proofs/{id}")
    ResponseEntity<byte[]> proof(@PathVariable String id) {
        var proof = attempts.readProof(workerId(), id(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(proof.bytes());
    }
    private long workerId() {
        var user = users.sharedReadCurrentUser();
        if (user.role() != SharedUserRole.DELIVERY_WORKER) throw denied();
        return user.userID();
    }
}
