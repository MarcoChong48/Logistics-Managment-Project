package hk.edu.hkmu.lms.delivery.operations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import hk.edu.hkmu.lms.delivery.access.DeliveryAccess;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;

/**
 * One transaction protects changes to delivery state and the saved response to a request.
 * Workflow callbacks run inside the transaction entered through this Spring bean.
 */
@Service
public class DeliveryTransactions {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Clock clock;
    private final DeliveryAccess access;

    public DeliveryTransactions(JdbcTemplate db, ObjectMapper json, Clock clock, DeliveryAccess access) {
        this.db = db;
        this.json = json;
        this.clock = clock;
        this.access = access;
    }

    @Transactional
    public Map<String, Object> command(String actor, String action, String target,
                                       Map<String, Object> body, Supplier<String> apply) {
        lock(db);
        access.user(actor);
        String requestId = requiredText(body, "requestId", 40);
        return once(actor, action + ":" + target, requestId, withoutRequestId(body), () -> {
            if (action.startsWith("DEMO_") && !access.demoEnabled()) {
                throw new ApiException(403, "DEMO_DISABLED", "Place holder controls are disabled outside the demo profile.");
            }
            return fields("message", apply.get());
        });
    }

    @Transactional
    public <T> T withWorker(String actor, Supplier<T> work) {
        lock(db);
        access.user(actor);
        return work.get();
    }

    // Proof uploads enter through withWorker and share the JSON commands' request journal.
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> once(String actor, String action, String requestId,
                                    Object payload, Supplier<Map<String, Object>> apply) {
        checkUuid(requestId);
        String hash = hashJson(payload);
        Map<String, Object> earlier = saved(actor, action, requestId, hash);
        if (earlier != null) return earlier;
        Map<String, Object> result = apply.get();
        storeResult(actor, action, requestId, hash, result);
        return result;
    }

    private static void checkUuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw new IllegalArgumentException();}catch(IllegalArgumentException ex){throw ApiException.bad("Use a valid requestId.");}}
    private static Map<String,Object> withoutRequestId(Map<String,Object> body){Map<String,Object> result=new TreeMap<>(body);result.remove("requestId");return result;}
    private String hashJson(Object body){try{return digest(json.writeValueAsBytes(body));}catch(Exception ex){throw ApiException.bad("The submitted fields could not be read.");}}
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private Map<String,Object> saved(String actor,String action,String request,String hash){
        Map<String,Object> row=optionalRow(db,"SELECT * FROM operation_result WHERE actor_id=? AND action_name=? AND request_id=?",actor,action,request);
        if(row==null)return null;if(!hash.equals(text(row,"PAYLOAD_HASH")))throw new ApiException(409,"REQUEST_KEY_REUSED","This request key was already used with different fields. Refresh and try again.");
        try{return json.readValue(text(row,"RESULT_JSON"),new TypeReference<>(){});}catch(Exception ex){throw new IllegalStateException("Saved result unreadable",ex);}
    }
    private void storeResult(String actor,String action,String request,String hash,Map<String,Object> result){try{db.update("INSERT INTO operation_result VALUES(?,?,?,?,?,?)",actor,action,request,hash,json.writeValueAsString(result),now());}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException(ex);}}

    private String now() {
        return clock.instant().toString();
    }
}
