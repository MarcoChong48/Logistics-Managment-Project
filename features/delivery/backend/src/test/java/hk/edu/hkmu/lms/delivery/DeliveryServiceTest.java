package hk.edu.hkmu.lms.delivery;

import hk.edu.hkmu.lms.delivery.attempts.DeliveryAttempts;
import hk.edu.hkmu.lms.delivery.demo.DemoSeed;
import hk.edu.hkmu.lms.delivery.pickup.PickupOperations;
import hk.edu.hkmu.lms.delivery.returns.PackageReturns;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import hk.edu.hkmu.lms.delivery.support.Fields;
import hk.edu.hkmu.lms.delivery.workspace.DeliveryWorkspace;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DeliveryServiceTest {
    @Autowired PickupOperations pickup;
    @Autowired DeliveryAttempts attempts;
    @Autowired PackageReturns returns;
    @Autowired DeliveryWorkspace workspaceQuery;
    @Autowired DemoSeed seed;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    static final Path FILES=Path.of("target","test-private-proofs").toAbsolutePath();
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:h2:mem:delivery-test;DB_CLOSE_DELAY=-1;DEFAULT_LOCK_TIMEOUT=10000");
        registry.add("app.data-dir",()->FILES.toString());
        registry.add("app.maintenance-delay-ms",()->"86400000");
    }
    @BeforeEach void reset(){
        // This test-only in-memory database never touches the user's persisted demo work.
        db.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for(String table:List.of("delivery_proof","delivery_attempt","pickup_cycle","return_line","return_receipt","client_instruction","discrepancy_case","stock_movement","custody_event","audit_event","security_event","notification_outbox","operation_result","retained_summary","maintenance_status","pickup_item","pickup_list","stock_allocation","delivery_package","order_item","delivery_detail","delivery_order","product_stock","app_account"))db.execute("TRUNCATE TABLE "+table);
        db.execute("SET REFERENTIAL_INTEGRITY TRUE");seed.run(null);
    }
    private Map<String,Object> body(Object...pairs){Map<String,Object> value=Fields.fields(pairs);value.put("requestId",UUID.randomUUID().toString());return value;}
    private void action(String actor,String name,String target,Object...pairs){mutate(actor,name,target,body(pairs));}
    private Map<String, Object> mutate(String actor, String name, String target, Map<String, Object> body) {
        return switch (name) {
            case "CLAIM" -> pickup.claim(actor, body);
            case "COLLECT" -> pickup.collect(actor, target, body);
            case "CANNOT_COLLECT" -> pickup.cannotCollect(actor, target, body);
            case "CLOSE" -> pickup.close(actor, target, body);
            case "START" -> attempts.start(actor, target, body);
            case "OUTCOME" -> attempts.outcome(actor, target, body);
            case "RETURN" -> returns.report(actor, target, body);
            case "DEMO_CONFIRM_RETURN" -> returns.confirmDemo(actor, target, body);
            case "DEMO_INSTRUCTION" -> returns.instructDemo(actor, target, body);
            case "DEMO_DECISION" -> returns.decideDemo(actor, target, body);
            case "DEMO_RELEASE" -> pickup.releaseDemo(actor, target, body);
            default -> throw new IllegalArgumentException(name);
        };
    }
    private String text(String sql,Object...args){return db.queryForObject(sql,String.class,args);}
    private int number(String sql,Object...args){return db.queryForObject(sql,Integer.class,args);}
    private void collected(String actor,String pkg){action(actor,"CLAIM","","qrCode","PL-1001");action(actor,"COLLECT","PL-1001","packageQrCode",pkg);}
    private void failedAndReported(){collected("ken","PKG-1001");action("ken","START","PKG-1001");action("ken","OUTCOME","PKG-1001","outcome","FAILED","reason","CLIENT_NOT_HOME");action("ken","RETURN","PKG-1001","reason","Returning after failed delivery");}
    private void expectStatus(int status,org.assertj.core.api.ThrowableAssert.ThrowingCallable work){assertThatThrownBy(work).isInstanceOfSatisfying(ApiException.class,ex->assertThat(ex.status()).isEqualTo(status));}

    @Test void claimDoesNotShipAndPickupIsAtomicIdempotentAndOwned(){
        action("ken","CLAIM","","qrCode","PL-1001");
        assertThat(text("SELECT order_status FROM delivery_order WHERE id='ORD-1001'")).isEqualTo("READY_FOR_PICKUP");
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(40);
        assertThat(text("SELECT custody FROM delivery_package WHERE id='PKG-1001'")).isEqualTo("WAREHOUSE");
        expectStatus(403,()->action("mei","COLLECT","PL-1001","packageQrCode","PKG-1001"));
        Map<String,Object> request=body("packageQrCode","PKG-1001");
        Map<String,Object> first=mutate("ken","COLLECT","PL-1001",request);
        assertThat(mutate("ken","COLLECT","PL-1001",request)).isEqualTo(first);
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(38);
        assertThat(number("SELECT reserved_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(1);
        assertThat(number("SELECT COUNT(*) FROM stock_movement WHERE movement_type='PICKUP'")).isEqualTo(2);
        expectStatus(409,()->action("ken","COLLECT","PL-1001","packageQrCode","PKG-1001"));
        request.put("packageQrCode","PKG-1002");expectStatus(409,()->mutate("ken","COLLECT","PL-1001",request));
        assertThat(text("SELECT custody FROM delivery_package WHERE id='PKG-1002'")).isEqualTo("WAREHOUSE");
    }

    @Test void concurrentClaimsHaveOneWinner() throws Exception{
        try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Boolean> ken=()->{try{action("ken","CLAIM","","qrCode","PL-1001");return true;}catch(ApiException ex){assertThat(ex.status()).isEqualTo(409);return false;}};
            Callable<Boolean> mei=()->{try{action("mei","CLAIM","","qrCode","PL-1001");return true;}catch(ApiException ex){assertThat(ex.status()).isEqualTo(409);return false;}};
            var results=executor.invokeAll(List.of(ken,mei));
            assertThat(results.stream().filter(f->{try{return f.get();}catch(Exception ex){throw new RuntimeException(ex);}}).count()).isEqualTo(1);
        }
    }

    @Test void laterLineFailureRollsBackEarlierStockAndCustodyChanges(){
        action("ken","CLAIM","","qrCode","PL-1001");
        db.update("UPDATE product_stock SET reserved_stock=0 WHERE sku='NOTE-01'");
        expectStatus(409,()->action("ken","COLLECT","PL-1001","packageQrCode","PKG-1001"));
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(40);
        assertThat(number("SELECT reserved_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(3);
        assertThat(number("SELECT COUNT(*) FROM stock_movement")).isZero();
        assertThat(number("SELECT COUNT(*) FROM pickup_cycle")).isZero();
        assertThat(text("SELECT custody FROM delivery_package WHERE id='PKG-1001'")).isEqualTo("WAREHOUSE");
        assertThat(text("SELECT collection_outcome FROM pickup_item WHERE list_id='PL-1001' AND package_id='PKG-1001'")).isEqualTo("PENDING");
    }

    @Test void proofMustBeRealNonBlankBoundToActiveAttemptAndPrivate() throws Exception{
        expectStatus(403,()->action("ken","START","PKG-1001"));collected("ken","PKG-1001");action("ken","START","PKG-1001");
        expectStatus(400,()->action("ken","OUTCOME","PKG-1001","outcome","DELIVERED","proofIds",List.of()));
        expectStatus(400,()->attempts.upload("ken","PKG-1001","PHOTO",new MockMultipartFile("file","bad.png","image/png","not an image".getBytes()),UUID.randomUUID().toString()));
        BufferedImage blank=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);ByteArrayOutputStream blankBytes=new ByteArrayOutputStream();ImageIO.write(blank,"png",blankBytes);
        expectStatus(400,()->attempts.upload("ken","PKG-1001","SIGNATURE",new MockMultipartFile("file","blank.png","image/png",blankBytes.toByteArray()),UUID.randomUUID().toString()));
        MockMultipartFile image=signature();String uploadKey=UUID.randomUUID().toString();
        String proof=attempts.upload("ken","PKG-1001","SIGNATURE",image,uploadKey).get("id").toString();
        assertThat(attempts.proof("ken",proof).bytes()).isNotEmpty();expectStatus(403,()->attempts.proof("mei",proof));
        action("ken","OUTCOME","PKG-1001","outcome","DELIVERED","proofIds",List.of(proof));
        assertThat(text("SELECT order_status FROM delivery_order WHERE id='ORD-1001'")).isEqualTo("DELIVERED");
        expectStatus(403,()->attempts.proof("ken",proof));
        assertThat(attempts.upload("ken","PKG-1001","SIGNATURE",image,uploadKey).get("id")).isEqualTo(proof);
        String workspace=json.writeValueAsString(workspaceQuery.workspace("ken"));
        assertThat(workspace).doesNotContain("Demo Recipient One","+852 6123 4567","Demo address: Kowloon");
        assertThat(number("SELECT COUNT(*) FROM delivery_attempt")).isEqualTo(1);
    }

    @Test void failedDeliveryRequiresWarehouseClientAdminAndNewPickup(){
        collected("ken","PKG-1001");action("ken","START","PKG-1001");
        expectStatus(400,()->action("ken","OUTCOME","PKG-1001","outcome","FAILED","reason","OTHER","note",""));
        action("ken","OUTCOME","PKG-1001","outcome","FAILED","reason","OTHER","note","Internal investigation detail");
        assertThat(text("SELECT safe_message FROM notification_outbox WHERE event_type='DELIVERY_FAILED'")).doesNotContain("Internal investigation");
        action("ken","RETURN","PKG-1001","reason","Return to Warehouse");
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(38);
        expectStatus(409,()->action("ken","DEMO_INSTRUCTION","PKG-1001","instruction","REDELIVER"));
        action("ken","DEMO_CONFIRM_RETURN","PKG-1001","condition","INTACT");
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(40);
        assertThat(number("SELECT reserved_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(3);
        expectStatus(409,()->action("ken","DEMO_DECISION","PKG-1001","decision","APPROVE_REDELIVERY"));
        action("ken","DEMO_INSTRUCTION","PKG-1001","instruction","REDELIVER");action("ken","DEMO_DECISION","PKG-1001","decision","APPROVE_REDELIVERY");
        expectStatus(403,()->action("ken","START","PKG-1001"));
        String list=text("SELECT l.id FROM pickup_list l JOIN pickup_item i ON i.list_id=l.id WHERE i.package_id='PKG-1001' AND l.list_status='OPEN'");
        action("mei","CLAIM","","qrCode",list);action("mei","COLLECT",list,"packageQrCode","PKG-1001");action("mei","START","PKG-1001");
        assertThat(number("SELECT COUNT(*) FROM delivery_attempt WHERE package_id='PKG-1001'")).isEqualTo(2);
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(38);
        expectStatus(403,()->action("ken","OUTCOME","PKG-1001","outcome","FAILED","reason","CLIENT_NOT_HOME"));
        action("mei","OUTCOME","PKG-1001","outcome","FAILED","reason","UNABLE_TO_CONTACT");
        action("mei","RETURN","PKG-1001","reason","Second failed attempt return");action("mei","DEMO_CONFIRM_RETURN","PKG-1001","condition","INTACT");
        expectStatus(409,()->action("mei","DEMO_DECISION","PKG-1001","decision","APPROVE_REDELIVERY"));
    }

    @Test void changedCancellationInstructionBlocksOldApprovalAndKeepsDamagedStockUnavailable(){
        failedAndReported();action("ken","DEMO_CONFIRM_RETURN","PKG-1001","condition","DAMAGED");
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(40);
        assertThat(number("SELECT unavailable_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(2);
        action("ken","DEMO_INSTRUCTION","PKG-1001","instruction","REDELIVER");
        expectStatus(409,()->action("ken","DEMO_DECISION","PKG-1001","decision","APPROVE_REDELIVERY"));
        action("ken","DEMO_INSTRUCTION","PKG-1001","instruction","CANCEL");
        expectStatus(409,()->action("ken","DEMO_DECISION","PKG-1001","decision","APPROVE_REDELIVERY"));
        assertThat(number("SELECT COUNT(*) FROM client_instruction WHERE instruction_status='SUPERSEDED'")).isEqualTo(1);
        action("ken","DEMO_DECISION","PKG-1001","decision","APPROVE_CANCELLATION");
        assertThat(text("SELECT order_status FROM delivery_order WHERE id='ORD-1001'")).isEqualTo("CANCELLED");
        assertThat(number("SELECT physical_stock-unavailable_stock-reserved_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(37);
        expectStatus(409,()->action("ken","DEMO_INSTRUCTION","PKG-1001","instruction","REDELIVER"));
        expectStatus(403,()->action("ken","START","PKG-1001"));
    }

    @Test void preAttemptReturnDoesNotInventAnAttemptAndCanBeCollectedAgain(){
        collected("ken","PKG-1001");action("ken","RETURN","PKG-1001","reason","Vehicle problem before delivery");
        assertThat(number("SELECT COUNT(*) FROM delivery_attempt")).isZero();
        action("ken","DEMO_CONFIRM_RETURN","PKG-1001","condition","INTACT");
        assertThat(text("SELECT order_status FROM delivery_order WHERE id='ORD-1001'")).isEqualTo("READY_FOR_PICKUP");
        expectStatus(409,()->action("ken","DEMO_CONFIRM_RETURN","PKG-1001","condition","INTACT"));
        String list=text("SELECT l.id FROM pickup_list l JOIN pickup_item i ON i.list_id=l.id WHERE i.package_id='PKG-1001' AND l.list_status='OPEN'");
        action("mei","CLAIM","","qrCode",list);action("mei","COLLECT",list,"packageQrCode","PKG-1001");
        assertThat(number("SELECT COUNT(*) FROM pickup_cycle WHERE package_id='PKG-1001'")).isEqualTo(2);
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(38);
    }

    @Test void supervisedReleaseNeverTransfersCollectedCustodyAndRejectsLateScans(){
        collected("ken","PKG-1001");
        db.update("UPDATE pickup_list SET last_activity_at=? WHERE id='PL-1001'",Instant.now().minus(Duration.ofMinutes(31)).toString());
        assertThat(workspaceQuery.workspace("ken").get("reviewQueue").toString()).contains("STALE_CLAIM");
        action("mei","DEMO_RELEASE","PL-1001");
        assertThat(text("SELECT holder_id FROM delivery_package WHERE id='PKG-1001'")).isEqualTo("ken");
        assertThat(number("SELECT physical_stock FROM product_stock WHERE sku='MUG-01'")).isEqualTo(38);
        expectStatus(409,()->action("ken","COLLECT","PL-1001","packageQrCode","PKG-1002"));
        String replacement=text("SELECT l.id FROM pickup_list l JOIN pickup_item i ON i.list_id=l.id WHERE i.package_id='PKG-1002' AND l.list_status='OPEN'");
        action("mei","CLAIM","","qrCode",replacement);action("mei","COLLECT",replacement,"packageQrCode","PKG-1002");
        assertThat(text("SELECT holder_id FROM delivery_package WHERE id='PKG-1002'")).isEqualTo("mei");
    }

    @Test void expiredProofAndAttemptAreDeletedButMinimalSummarySurvives() throws Exception{
        collected("ken","PKG-1001");action("ken","START","PKG-1001");
        String proof=attempts.upload("ken","PKG-1001","SIGNATURE",signature(),UUID.randomUUID().toString()).get("id").toString();
        action("ken","OUTCOME","PKG-1001","outcome","DELIVERED","proofIds",List.of(proof));
        db.update("UPDATE delivery_attempt SET ended_at=?",Instant.now().minus(Duration.ofDays(366)).toString());
        attempts.maintenance();
        assertThat(number("SELECT COUNT(*) FROM delivery_attempt")).isZero();assertThat(number("SELECT COUNT(*) FROM delivery_proof")).isZero();
        assertThat(number("SELECT COUNT(*) FROM retained_summary")).isEqualTo(1);
        assertThat(workspaceQuery.workspace("ken").get("retainedSummaries").toString()).contains("detailExpired=true");
    }

    @Test void httpUsesSessionCsrfAndGenericCredentialErrors() throws Exception{
        http.perform(get("/api/delivery/workspace")).andExpect(status().isUnauthorized());
        http.perform(post("/api/pickup-lists/claim").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        var initial=http.perform(get("/api/session")).andExpect(status().isOk()).andReturn();
        JsonNode token=json.readTree(initial.getResponse().getContentAsString());MockHttpSession session=(MockHttpSession)initial.getRequest().getSession(false);
        http.perform(post("/api/login").session(session).header(token.get("csrfHeader").asText(),token.get("csrfToken").asText()).param("username","ken").param("password","Delivery123!")).andExpect(status().isOk());
        http.perform(get("/api/delivery/workspace").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value("ken"));
        http.perform(get("/api/session").session(session)).andExpect(jsonPath("$.authenticated").value(true));
        db.update("UPDATE app_account SET account_status='DEACTIVATED' WHERE id='ken'");
        http.perform(get("/api/delivery/workspace").session(session)).andExpect(status().isForbidden());
    }

    private static MockMultipartFile signature() throws Exception {
        BufferedImage image=new BufferedImage(180,80,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,180,80);g.setColor(Color.BLACK);g.drawLine(20,20,140,60);g.drawLine(40,50,130,15);g.dispose();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        return new MockMultipartFile("file","signature.png","image/png",bytes.toByteArray());
    }
}
