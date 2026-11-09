package hk.edu.hkmu.lms.delivery.pickup;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;
import java.util.ArrayList;
import java.util.Set;
import hk.edu.hkmu.lms.delivery.fulfilment.FulfilmentState;
import hk.edu.hkmu.lms.delivery.operations.DeliveryTransactions;

@Service
public class PickupOperations {
    private final JdbcTemplate db;
    private final Clock clock;
    private final DeliveryTransactions transactions;
    private final FulfilmentState state;

    public PickupOperations(JdbcTemplate db, Clock clock, DeliveryTransactions transactions, FulfilmentState state) {
        this.db = db;
        this.clock = clock;
        this.transactions = transactions;
        this.state = state;
    }

    public Map<String, Object> claim(String actor, Map<String, Object> body) {
        return transactions.command(actor, "CLAIM", "", body, () -> claimList(actor, body));
    }

    public Map<String, Object> collect(String actor, String listId, Map<String, Object> body) {
        return transactions.command(actor, "COLLECT", listId, body, () -> collectPackage(actor, listId, body));
    }

    public Map<String, Object> cannotCollect(String actor, String listId, Map<String, Object> body) {
        return transactions.command(actor, "CANNOT_COLLECT", listId, body, () -> recordCannotCollect(actor, listId, body));
    }

    public Map<String, Object> close(String actor, String listId, Map<String, Object> body) {
        return transactions.command(actor, "CLOSE", listId, body, () -> closeList(actor, listId, false));
    }

    public Map<String, Object> releaseDemo(String actor, String listId, Map<String, Object> body) {
        return transactions.command(actor, "DEMO_RELEASE", listId, body, () -> closeList(actor, listId, true));
    }

    private String claimList(String actor,Map<String,Object> body) {
        String qr=requiredText(body,"qrCode",80);
        Map<String,Object> list=requiredRow(db,"SELECT * FROM pickup_list WHERE qr_code=?",qr);
        if("CLOSED".equals(text(list,"LIST_STATUS")))throw ApiException.conflict("This pickup list is closed. Scan its current replacement list.");
        String owner=text(list,"CLAIMED_BY");
        if(owner!=null&&!owner.equals(actor))throw ApiException.conflict("Another delivery worker already claimed this list.");
        if(actor.equals(owner))return "Your pickup list is ready to continue.";
        db.update("UPDATE pickup_list SET list_status='CLAIMED',claimed_by=?,claimed_at=?,last_activity_at=? WHERE id=?",actor,now(),now(),text(list,"ID"));
        state.audit(actor,"CLAIM",text(list,"ID"),"List claimed. Custody and stock unchanged.");
        return "Pickup list claimed. Scan each package to record actual collection.";
    }

    private String collectPackage(String actor,String listId,Map<String,Object> body) {
        collector(actor,listId);
        String qr=requiredText(body,"packageQrCode",80);
        Map<String,Object> p=requiredRow(db,"SELECT p.*,o.order_status,o.order_reference,o.client_id,o.payment_accepted FROM delivery_package p JOIN delivery_order o ON o.id=p.order_id WHERE p.qr_code=?",qr);
        String packageId=text(p,"ID");
        Map<String,Object> item=optionalRow(db,"SELECT * FROM pickup_item WHERE list_id=? AND package_id=?",listId,packageId);
        if(item==null)throw ApiException.conflict("This package does not belong to this pickup list.");
        if(!"PENDING".equals(text(item,"COLLECTION_OUTCOME")))throw ApiException.conflict("This list item is already collected or closed for collection.");
        if (!state.eligible(p)) throw ApiException.conflict("This package is not ready for this pickup. Check holds, return and redelivery approval.");
        String cycle=newId("PICK");
        List<Map<String,Object>> products=state.items(text(p,"ORDER_ID"));
        for(Map<String,Object> product:products){
            String sku=text(product,"SKU");int quantity=number(product,"QUANTITY");
            Map<String,Object> stock=requiredRow(db,"SELECT * FROM product_stock WHERE sku=?",sku);
            Map<String,Object> allocation=requiredRow(db,"SELECT * FROM stock_allocation WHERE package_id=? AND sku=?",packageId,sku);
            if(number(allocation,"QUANTITY")!=quantity||number(stock,"RESERVED_STOCK")<quantity||number(stock,"PHYSICAL_STOCK")-number(stock,"UNAVAILABLE_STOCK")<quantity)
                throw ApiException.conflict("Stock needs Warehouse reconciliation before this package can be collected.");
            db.update("UPDATE product_stock SET physical_stock=physical_stock-?,reserved_stock=reserved_stock-? WHERE sku=?",quantity,quantity,sku);
            db.update("UPDATE stock_allocation SET quantity=0 WHERE package_id=? AND sku=?",packageId,sku);
            state.movement(sku,packageId,cycle,"PICKUP",-quantity,-quantity,0,actor);
        }
        // Decision U-10, B: One local transaction records stock, list, order and actual custody.
        db.update("INSERT INTO pickup_cycle VALUES(?,?,?,?,?,?)",cycle,packageId,listId,actor,now(),text(p,"APPROVED_INSTRUCTION_ID"));
        db.update("UPDATE pickup_item SET collection_outcome='COLLECTED',collected_at=? WHERE list_id=? AND package_id=?",now(),listId,packageId);
        db.update("UPDATE delivery_package SET custody='DELIVERY',holder_id=?,responsible_id=?,last_pickup_id=?,version_no=version_no+1 WHERE id=?",actor,actor,cycle,packageId);
        state.setStatus(p,"SHIPPED");touch(listId);
        state.custody(packageId,cycle,"PICKUP","WAREHOUSE",actor,actor);
        state.audit(actor,"COLLECT",packageId,"Actual package pickup committed with stock movement.");
        state.notify(actor,p,"SHIPPED","Package collected. Place holder notification transport must notify the Client.");
        return "Package collected and stock movement saved.";
    }

    private String recordCannotCollect(String actor,String listId,Map<String,Object> body) {
        collector(actor,listId);String packageId=requiredText(body,"packageId",60);
        String reason=requiredText(body,"reason",40),note=optionalText(body,"note",600);
        if(!Set.of("VEHICLE_FULL","PACKAGING_DAMAGED","GOODS_DAMAGED","OTHER").contains(reason))throw ApiException.bad("Choose a collection problem reason.");
        if("OTHER".equals(reason)&&note.isBlank())throw ApiException.bad("Add a note for Other.");
        Map<String,Object> item=requiredRow(db,"SELECT * FROM pickup_item WHERE list_id=? AND package_id=?",listId,packageId);
        if(!"PENDING".equals(text(item,"COLLECTION_OUTCOME")))throw ApiException.conflict("This list item has already been handled.");
        Map<String,Object> p=state.packageRow(packageId);if(!"WAREHOUSE".equals(text(p,"CUSTODY")))throw ApiException.conflict("A collected package needs a recorded return instead.");
        db.update("UPDATE pickup_item SET collection_outcome='CANNOT_COLLECT',reason=?,internal_note=? WHERE list_id=? AND package_id=?",reason,note,listId,packageId);
        if(!"VEHICLE_FULL".equals(reason)) {
            db.update("UPDATE delivery_package SET hold_reason=?,version_no=version_no+1 WHERE id=?",reason,packageId);
            state.discrepancy(packageId,listId,reason,actor,"Warehouse review required before collection. Place holder: Warehouse/Admin owners resolve this hold.");
        }
        touch(listId);state.audit(actor,"CANNOT_COLLECT",packageId,"Reason: "+reason+". No customer delivery attempt created.");
        return "Collection problem saved. No stock or custody change was made.";
    }

    private String closeList(String actor,String listId,boolean supervised) {
        Map<String,Object> list=requiredRow(db,"SELECT * FROM pickup_list WHERE id=?",listId);
        if("CLOSED".equals(text(list,"LIST_STATUS")))throw ApiException.conflict("This pickup list is already closed.");
        if(supervised) {
            if(text(list,"CLAIMED_BY")==null)throw ApiException.conflict("Only an abandoned claimed list needs supervised release.");
        }else collector(actor,listId);
        String eventActor=supervised?"DEMO_ADMIN":actor;
        List<String> eligible=new ArrayList<>();
        for(Map<String,Object> row:db.queryForList("SELECT * FROM pickup_item WHERE list_id=?",listId)) {
            if("COLLECTED".equals(text(row,"COLLECTION_OUTCOME")))continue;
            String packageId=text(row,"PACKAGE_ID");Map<String,Object> p=state.packageRow(packageId);
            if(!"WAREHOUSE".equals(text(p,"CUSTODY")))throw ApiException.conflict("Warehouse presence must be confirmed before an uncollected entry can be released.");
            db.update("UPDATE pickup_item SET collection_outcome='RELEASED' WHERE list_id=? AND package_id=?",listId,packageId);
            if(state.eligible(p))eligible.add(packageId);
        }
        db.update("UPDATE pickup_list SET list_status='CLOSED',last_activity_at=? WHERE id=?",now(),listId);
        String replacement=state.makeList(eligible);
        state.audit(eventActor,supervised?"PLACE_HOLDER_RELEASE":"CLOSE_LIST",listId,supervised?"Place holder: Warehouse presence and Admin release simulated by "+actor:"Uncollected history preserved. Collected custody unchanged.");
        return (supervised?"Place holder: Warehouse presence and Admin release simulated. ":"Pickup list closed. ")+(replacement==null?"No eligible uncollected packages need a new list.":"Eligible packages are on "+replacement+".");
    }
    private Map<String,Object> collector(String actor,String id) {
        Map<String,Object> list=requiredRow(db,"SELECT * FROM pickup_list WHERE id=?",id);
        if("CLOSED".equals(text(list,"LIST_STATUS")))throw ApiException.conflict("This pickup list is closed. Refresh before scanning.");
        if(!actor.equals(text(list,"CLAIMED_BY")))throw ApiException.forbidden();
        return list;
    }
    private void touch(String listId){db.update("UPDATE pickup_list SET last_activity_at=? WHERE id=?",now(),listId);}

    private String now() {
        return clock.instant().toString();
    }
}
