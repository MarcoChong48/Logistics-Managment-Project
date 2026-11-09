package hk.edu.hkmu.lms.delivery.attempts;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
class AttemptController {
    private final DeliveryAttempts service;

    AttemptController(DeliveryAttempts service) {
        this.service = service;
    }

    @PostMapping("/packages/{id}/start")
    Map<String, Object> start(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.start(auth.getName(), id, body);
    }

    @PostMapping(value="/packages/{id}/proofs",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> proof(Authentication auth, @PathVariable String id, @RequestParam String type, @RequestParam MultipartFile file, @RequestParam(required=false) String requestId) {
        return service.upload(auth.getName(), id, type, file, requestId);
    }

    @PostMapping("/packages/{id}/outcome")
    Map<String, Object> outcome(Authentication auth, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.outcome(auth.getName(), id, body);
    }

    @GetMapping("/proofs/{id}")
    ResponseEntity<byte[]> readProof(Authentication auth, @PathVariable String id) {
        DeliveryAttempts.ProofContent proof=service.proof(auth.getName(), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .contentType(MediaType.parseMediaType(proof.contentType())).body(proof.bytes());
    }
}
