package hk.edu.hkmu.lms.delivery.workspace;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import hk.edu.hkmu.lms.delivery.access.DeliveryAccess;
import hk.edu.hkmu.lms.delivery.fulfilment.FulfilmentState;

@Service
public class DeliveryWorkspace {
    private final JdbcTemplate db;
    private final Clock clock;
    private final DeliveryAccess access;
    private final FulfilmentState state;
    private final int proofLimit, staleMinutes, reviewFailures, reminderDays, finalDays;

    public DeliveryWorkspace(JdbcTemplate db, Clock clock, DeliveryAccess access, FulfilmentState state,
                             @Value("${app.photo-limit-bytes:5242880}") int proofLimit,
                             @Value("${app.stale-claim-minutes:30}") int staleMinutes,
                             @Value("${app.review-failure-count:2}") int reviewFailures,
                             @Value("${app.reminder-days:3}") int reminderDays,
                             @Value("${app.final-review-days:7}") int finalDays) {
        this.db = db;
        this.clock = clock;
        this.access = access;
        this.state = state;
        this.proofLimit = proofLimit;
        this.staleMinutes = staleMinutes;
        this.reviewFailures = reviewFailures;
        this.reminderDays = reminderDays;
        this.finalDays = finalDays;
    }

    @Transactional
    public Map<String,Object> workspace(String actor) {
        lock(db);Map<String,Object> currentUser=access.user(actor);
        List<Map<String,Object>> lists=new ArrayList<>(),tasks=new ArrayList<>(),attempts=new ArrayList<>(),returns=new ArrayList<>(),notifications=new ArrayList<>(),demoPackages=new ArrayList<>();
        for(Map<String,Object> list:db.queryForList("SELECT * FROM pickup_list WHERE list_status='OPEN' OR claimed_by=? ORDER BY created_at DESC,id",actor)) {
            List<Map<String,Object>> packages=new ArrayList<>();
            for(Map<String,Object> item:db.queryForList("SELECT * FROM pickup_item WHERE list_id=? ORDER BY package_id",text(list,"ID"))){
                Map<String,Object> p=state.packageRow(text(item,"PACKAGE_ID"));
                String summary=String.join(", ",state.items(text(p,"ORDER_ID")).stream().map(i->number(i,"QUANTITY")+" × "+text(i,"PRODUCT_NAME")).toList());
                packages.add(fields("id",text(p,"ID"),"qrCode",text(p,"QR_CODE"),"orderReference",text(p,"ORDER_REFERENCE"),"status",text(p,"ORDER_STATUS"),"itemSummary",summary,"collectionOutcome",text(item,"COLLECTION_OUTCOME"),"cannotCollectReason",text(item,"REASON"),"holdReason",text(p,"HOLD_REASON")));
            }
            lists.add(fields("id",text(list,"ID"),"qrCode",text(list,"QR_CODE"),"status",text(list,"LIST_STATUS"),"createdAt",text(list,"CREATED_AT"),"claimedBy",text(list,"CLAIMED_BY"),"claimedAt",text(list,"CLAIMED_AT"),"stale",stale(list),"packages",packages));
        }
        for(Map<String,Object> p:db.queryForList("SELECT p.*,o.order_status,o.order_reference,o.client_id,o.payment_accepted FROM delivery_package p JOIN delivery_order o ON o.id=p.order_id WHERE p.holder_id=? AND p.custody='DELIVERY' ORDER BY p.id",actor)){
            String packageId=text(p,"ID");Map<String,Object> detail=requiredRow(db,"SELECT * FROM delivery_detail WHERE id=?",text(p,"DETAIL_ID"));
            Map<String,Object> active=optionalRow(db,"SELECT * FROM delivery_attempt WHERE package_id=? AND worker_id=? AND outcome='IN_PROGRESS'",packageId,actor);
            boolean pending=state.pendingReturn(packageId),responsible=actor.equals(text(p,"RESPONSIBLE_ID"));String status=text(p,"ORDER_STATUS");
            boolean canStart=responsible&&!pending&&"SHIPPED".equals(status),canOutcome=responsible&&!pending&&"OUT_FOR_DELIVERY".equals(status)&&active!=null;
            List<Map<String,Object>> products=state.items(text(p,"ORDER_ID")).stream().map(i->fields("sku",text(i,"SKU"),"name",text(i,"PRODUCT_NAME"),"quantity",number(i,"QUANTITY"))).toList();
            tasks.add(fields("id",packageId,"qrCode",text(p,"QR_CODE"),"orderReference",text(p,"ORDER_REFERENCE"),"status",status,"custody",text(p,"CUSTODY"),
                "recipientName",responsible?text(detail,"RECIPIENT_NAME"):null,"phone",responsible?text(detail,"PHONE"):null,"address",responsible?text(detail,"ADDRESS_TEXT"):null,
                "products",products,"attemptCount",attemptCount(packageId),"activeAttemptId",active==null?null:text(active,"ID"),"canStart",canStart,"canDeliver",canOutcome,"canFail",canOutcome,
                "canReturn",responsible&&!pending&&Set.of("SHIPPED","DELIVERY_FAILED").contains(status),"returnPending",pending));
        }
        for(Map<String,Object> attempt:db.queryForList("SELECT a.*,u.display_name,o.order_reference FROM delivery_attempt a JOIN app_account u ON u.id=a.worker_id JOIN delivery_package p ON p.id=a.package_id JOIN delivery_order o ON o.id=p.order_id WHERE a.worker_id=? ORDER BY a.started_at DESC,a.id DESC LIMIT 300",actor)){
            List<Map<String,Object>> proofs=new ArrayList<>();
            Map<String,Object> p=state.packageRow(text(attempt,"PACKAGE_ID"));
            boolean mayView="IN_PROGRESS".equals(text(attempt,"OUTCOME"))&&"OUT_FOR_DELIVERY".equals(text(p,"ORDER_STATUS"))&&actor.equals(text(p,"HOLDER_ID"))&&actor.equals(text(p,"RESPONSIBLE_ID"))&&"DELIVERY".equals(text(p,"CUSTODY"));
            for(Map<String,Object> proof:db.queryForList("SELECT * FROM delivery_proof WHERE attempt_id=? ORDER BY created_at",text(attempt,"ID")))
                proofs.add(fields("id",text(proof,"ID"),"type",text(proof,"PROOF_TYPE"),"createdAt",text(proof,"CREATED_AT"),"url",mayView?"/api/proofs/"+text(proof,"ID"):null));
            attempts.add(fields("id",text(attempt,"ID"),"packageId",text(attempt,"PACKAGE_ID"),"orderReference",text(attempt,"ORDER_REFERENCE"),"workerName",text(attempt,"DISPLAY_NAME"),"startedAt",text(attempt,"STARTED_AT"),"endedAt",text(attempt,"ENDED_AT"),"outcome",text(attempt,"OUTCOME"),"reason",text(attempt,"REASON"),"note",text(attempt,"INTERNAL_NOTE"),"proofs",proofs));
        }
        for(Map<String,Object> receipt:db.queryForList("SELECT r.*,o.order_reference FROM return_receipt r JOIN delivery_package p ON p.id=r.package_id JOIN delivery_order o ON o.id=p.order_id WHERE r.reported_by=? ORDER BY r.created_at DESC LIMIT 200",actor))
            returns.add(fields("id",text(receipt,"ID"),"packageId",text(receipt,"PACKAGE_ID"),"orderReference",text(receipt,"ORDER_REFERENCE"),"reason",text(receipt,"REASON"),"status",text(receipt,"RECEIPT_STATUS"),"createdAt",text(receipt,"CREATED_AT"),"receivedAt",text(receipt,"RECEIVED_AT"),"awaitingInstruction","RECEIVED".equals(text(receipt,"RECEIPT_STATUS"))&&rowCount(db,"SELECT COUNT(*) FROM client_instruction WHERE return_id=? AND instruction_status IN ('PENDING','APPROVED')",text(receipt,"ID"))==0,"condition",text(receipt,"RECEIPT_CONDITION")));
        for(Map<String,Object> event:db.queryForList("SELECT * FROM notification_outbox WHERE actor_id=? ORDER BY created_at DESC LIMIT 100",actor))
            notifications.add(fields("id",text(event,"ID"),"type",text(event,"EVENT_TYPE"),"message",text(event,"SAFE_MESSAGE"),"createdAt",text(event,"CREATED_AT"),"read",flag(event,"IS_READ")));
        if(access.demoEnabled())for(Map<String,Object> base:db.queryForList("SELECT id FROM delivery_package ORDER BY id")){
            Map<String,Object> p=state.packageRow(text(base,"ID"));Map<String,Object> receipt=text(p,"LAST_RETURN_ID")==null?null:state.latestReturn(p);
            Map<String,Object> ins=text(p,"LAST_RETURN_ID")==null?null:optionalRow(db,"SELECT * FROM client_instruction WHERE package_id=? AND return_id=? ORDER BY version_no DESC LIMIT 1",text(p,"ID"),text(p,"LAST_RETURN_ID"));
            demoPackages.add(fields("id",text(p,"ID"),"orderReference",text(p,"ORDER_REFERENCE"),"status",text(p,"ORDER_STATUS"),"custody",text(p,"CUSTODY"),"returnStatus",receipt==null?null:text(receipt,"RECEIPT_STATUS"),"returnCondition",receipt==null?null:text(receipt,"RECEIPT_CONDITION"),"instruction",ins==null?null:text(ins,"INSTRUCTION_TYPE"),"instructionStatus",ins==null?null:text(ins,"INSTRUCTION_STATUS"),"approved",flag(p,"RETRY_APPROVED"),"pendingCancellation",state.pendingCancellation(text(p,"ID")),"holdReason",text(p,"HOLD_REASON")));
        }
        ZoneId hongKong=ZoneId.of("Asia/Hong_Kong");
        Instant today=LocalDate.now(clock.withZone(hongKong)).atStartOfDay(hongKong).toInstant();
        Map<String,Object> stats=fields("readyLists",rowCount(db,"SELECT COUNT(*) FROM pickup_list WHERE list_status='OPEN'"),
            "inTransit",rowCount(db,"SELECT COUNT(*) FROM delivery_package p JOIN delivery_order o ON o.id=p.order_id WHERE p.holder_id=? AND p.custody='DELIVERY' AND o.order_status IN ('SHIPPED','OUT_FOR_DELIVERY')",actor),
            "deliveredToday",rowCount(db,"SELECT COUNT(*) FROM delivery_attempt WHERE worker_id=? AND outcome='DELIVERED' AND ended_at>=?",actor,today.toString()),
            "pendingReturns",rowCount(db,"SELECT COUNT(*) FROM return_receipt WHERE reported_by=? AND receipt_status='AWAITING_WAREHOUSE'",actor),
            "failedAttempts",rowCount(db,"SELECT COUNT(*) FROM delivery_attempt WHERE worker_id=? AND outcome='FAILED'",actor));
        List<Map<String,Object>> summaries=new ArrayList<>();
        for(Map<String,Object> summary:db.queryForList("SELECT s.*,o.order_reference FROM retained_summary s JOIN delivery_package p ON p.id=s.package_id JOIN delivery_order o ON o.id=p.order_id WHERE s.worker_id=? ORDER BY event_at DESC",actor))
            summaries.add(fields("packageId",text(summary,"PACKAGE_ID"),"orderReference",text(summary,"ORDER_REFERENCE"),"outcome",text(summary,"OUTCOME"),"eventAt",text(summary,"EVENT_AT"),"detailExpired",true));
        Map<String,Object> maintenance=optionalRow(db,"SELECT * FROM maintenance_status WHERE id='proof-retention'");
        return fields("user",currentUser,"mode",access.mode(),"stats",stats,"pickupLists",lists,"tasks",tasks,"attempts",attempts,"returns",returns,"notifications",notifications,
            "integrations",integrations(),"decisions",decisions(),"settings",fields("photoLimitBytes",proofLimit,"staleClaimMinutes",staleMinutes,"reviewFailureCount",reviewFailures,"reminderDays",reminderDays,"finalReviewDays",finalDays),
            "demoPackages",demoPackages,"reviews",reviews(actor),"reviewQueue",reviews(actor),"retainedSummaries",summaries,
            "storageHealth",fields("privateLocalStorage",true,"cloudConnector","Place holder","lastCleanupAt",maintenance==null?null:text(maintenance,"LAST_RUN_AT"),"lastError",maintenance==null?null:text(maintenance,"LAST_ERROR")));
    }
    private int attemptCount(String pkg){return rowCount(db,"SELECT COUNT(*) FROM delivery_attempt WHERE package_id=?",pkg)+rowCount(db,"SELECT COUNT(*) FROM retained_summary WHERE package_id=?",pkg);}
    private boolean stale(Map<String,Object> list){String activity=text(list,"LAST_ACTIVITY_AT");return "CLAIMED".equals(text(list,"LIST_STATUS"))&&activity!=null&&!clock.instant().isBefore(Instant.parse(activity).plus(Duration.ofMinutes(staleMinutes)));}
    private List<Map<String,Object>> reviews(String actor){
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> list:db.queryForList("SELECT * FROM pickup_list WHERE claimed_by=? AND list_status='CLAIMED'",actor))if(stale(list))result.add(fields("id","STALE-"+text(list,"ID"),"type","STALE_CLAIM","title","Stale pickup claim","reference",text(list,"ID"),"packageId",null,"orderReference",text(list,"ID"),"createdAt",text(list,"LAST_ACTIVITY_AT"),"message","Place holder: Warehouse presence confirmation and Admin release are required. No custody transfer is automatic."));
        for(Map<String,Object> p:db.queryForList("SELECT p.*,o.order_status,o.order_reference FROM delivery_package p JOIN delivery_order o ON o.id=p.order_id WHERE p.responsible_id=? AND o.order_status='DELIVERY_FAILED'",actor)){
            String pkg=text(p,"ID");int failures=rowCount(db,"SELECT COUNT(*) FROM delivery_attempt WHERE package_id=? AND outcome='FAILED'",pkg);
            if(failures>=reviewFailures)result.add(fields("id","FAILURES-"+pkg,"type","REPEATED_FAILURE","title","Repeated failures need review","reference",text(p,"ORDER_REFERENCE"),"packageId",pkg,"orderReference",text(p,"ORDER_REFERENCE"),"createdAt",now(),"message",failures+" failed attempts need Admin review. No automatic cancellation."));
            if(text(p,"LAST_RETURN_ID")!=null){Map<String,Object> receipt=state.latestReturn(p);String received=text(receipt,"RECEIVED_AT");
                if(received!=null&&!state.pendingCancellation(pkg)){
                    long days=Duration.between(Instant.parse(received),clock.instant()).toDays();
                    boolean hasInstruction=rowCount(db,"SELECT COUNT(*) FROM client_instruction WHERE return_id=? AND instruction_status='PENDING'",text(receipt,"ID"))>0;
                    if(!hasInstruction&&days>=reminderDays)result.add(fields("id","RESPONSE-"+text(receipt,"ID"),"type",days>=finalDays?"FINAL_REVIEW":"CLIENT_REMINDER","title",days>=finalDays?"Final response review":"Client response reminder","reference",text(p,"ORDER_REFERENCE"),"packageId",pkg,"orderReference",text(p,"ORDER_REFERENCE"),"createdAt",received,"message",days>=finalDays?"No Client instruction after return. Place holder: Admin reviews; no automatic closure or stock release.":"Client response reminder is due. Place holder: Client notification owner sends the reminder."));
                }
            }
        }
        return result;
    }
    private List<Map<String,Object>> integrations(){return List.of(
        fields("id","auth","name","Shared authentication","status","PLACE_HOLDER","detail","Place holder: Auth owner replaces local demo accounts with the shared account lifecycle and sessions."),
        fields("id","database","name","Database / inventory","status","PLACE_HOLDER","detail","Place holder: Database owner replaces provisional H2 tables and preserves transactional stock, custody and cycle rules."),
        fields("id","warehouse","name","Warehouse","status","PLACE_HOLDER","detail","Place holder: Warehouse owner provides packed lists, actual return quantities, condition evidence and repair clearance."),
        fields("id","client","name","Client","status","PLACE_HOLDER","detail","Place holder: Client owner supplies versioned instructions, delivery-detail snapshots and own-order proof authorization."),
        fields("id","admin","name","Admin","status","PLACE_HOLDER","detail","Place holder: Admin owner supplies approved redelivery/cancellation, shortage decisions and supervised recovery."),
        fields("id","notifications","name","Notifications / refunds","status","PLACE_HOLDER","detail","Place holder: Client/Admin payment owners deliver durable events and resolve real provider refund outcomes."),
        fields("id","storage","name","Private object storage","status","PLACE_HOLDER","detail","Place holder: Storage owner supplies private cloud credentials, authorized reads and retention reconciliation. Local files are private and persistent."),
        fields("id","hosting","name","Hosting / recovery","status","PLACE_HOLDER","detail","Place holder: Hosting owner supplies HTTPS, rehearsal deployment, managed backups and service alerts. This app is local only."));}
    private List<Map<String,Object>> decisions(){
        List<Map<String,Object>> result=new ArrayList<>();
        String[][] choices={{"U-01","Deployment / recovery hooks","Cloud hosting and automated backups remain Place holder integrations."},{"U-02","Repeatable broader acceptance","Configurable test data and timing boundaries; no benchmark pass is claimed."},{"U-03","API contracts and automated checks","Feature work can be reviewed and tested before integration."},{"U-04","Presentation evidence","Use the real local build and actual test evidence; recording remains team work."},{"U-05","Spring Boot JSON API + React","One runnable local app; shared framework and Database contracts remain Place holder."},{"U-06","Discrepancy cases and unavailable stock","Damaged returns keep their actual physical count and cannot become sellable."},{"U-07","Full re-verification gate","Place holder: Warehouse owner supplies packing revisions and clearance after approved repair."},{"U-08","Versioned client instructions","Place holder: Client/Auth owners supply recovery codes and short support authorization."},{"U-09","Private proof adapter and retention monitoring","Local private storage works; cloud connector remains Place holder."},{"U-10","Supervised stale-claim recovery","Flag inactivity without moving custody. Close old lists before releasing uncollected entries."},{"U-11","Current access and handover boundaries","No responsibility change can invent physical possession."},{"U-12","Review and response reminder queues","No automatic retry, cancellation, fee or final no-response closure."},{"U-13","Versioned delivery-detail input","Place holder: Client address book stays outside Delivery."},{"U-14","Delivery dashboard and saved-filter support","Only authorized data reaches the browser."},{"U-15","Refund outcome boundary","Place holder: Admin/payment owners recheck and reconcile refunds. Cancellation stays final."},{"U-16","Embedded payment boundary","Place holder: Client/payment owner supplies accepted payment eligibility; no card data enters Delivery."}};
        Set<String> external=Set.of("U-01","U-04","U-07","U-08","U-11","U-13","U-15","U-16");
        for(String[] choice:choices)result.add(fields("id",choice[0],"plan","B","choice",choice[1],"reason",choice[2],"status",external.contains(choice[0])?"PLACE_HOLDER":Set.of("U-02","U-03","U-09").contains(choice[0])?"PARTIAL_PLACE_HOLDER":"LOCAL_IMPLEMENTATION"));
        return result;
    }

    private String now() {
        return clock.instant().toString();
    }
}
