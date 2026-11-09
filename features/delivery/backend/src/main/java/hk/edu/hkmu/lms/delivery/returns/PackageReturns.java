package hk.edu.hkmu.lms.delivery.returns;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;
import java.util.Set;
import hk.edu.hkmu.lms.delivery.fulfilment.FulfilmentState;
import hk.edu.hkmu.lms.delivery.operations.DeliveryTransactions;

@Service
public class PackageReturns {
    private final JdbcTemplate db;
    private final Clock clock;
    private final DeliveryTransactions transactions;
    private final FulfilmentState state;

    public PackageReturns(JdbcTemplate db, Clock clock, DeliveryTransactions transactions, FulfilmentState state) {
        this.db = db;
        this.clock = clock;
        this.transactions = transactions;
        this.state = state;
    }

    public Map<String, Object> report(String actor, String packageId, Map<String, Object> body) {
        return transactions.command(actor, "RETURN", packageId, body, () -> reportReturn(actor, packageId, body));
    }

    public Map<String, Object> confirmDemo(String actor, String packageId, Map<String, Object> body) {
        return transactions.command(actor, "DEMO_CONFIRM_RETURN", packageId, body, () -> confirmReturn(actor, packageId, body));
    }

    public Map<String, Object> instructDemo(String actor, String packageId, Map<String, Object> body) {
        return transactions.command(actor, "DEMO_INSTRUCTION", packageId, body, () -> instruction(actor, packageId, body));
    }

    public Map<String, Object> decideDemo(String actor, String packageId, Map<String, Object> body) {
        return transactions.command(actor, "DEMO_DECISION", packageId, body, () -> decision(actor, packageId, body));
    }

    private String reportReturn(String actor,String packageId,Map<String,Object> body) {
        Map<String,Object> p=state.holder(actor,packageId);
        if(!Set.of("SHIPPED","DELIVERY_FAILED").contains(text(p,"ORDER_STATUS")))throw ApiException.conflict("Finish the active delivery attempt before reporting a return.");
        if(state.pendingReturn(packageId))throw ApiException.conflict("This package already awaits Warehouse receipt.");
        String reason=requiredText(body,"reason",600);String ret=newId("RET");
        Map<String,Object> last=optionalRow(db,"SELECT * FROM delivery_attempt WHERE package_id=? AND pickup_id=? ORDER BY started_at DESC LIMIT 1",packageId,text(p,"LAST_PICKUP_ID"));
        String attemptId=last==null?null:text(last,"ID");
        db.update("INSERT INTO return_receipt(id,package_id,pickup_id,attempt_id,reported_by,reason,receipt_status,created_at) VALUES(?,?,?,?,?,?,?,?)",ret,packageId,text(p,"LAST_PICKUP_ID"),attemptId,actor,reason,"AWAITING_WAREHOUSE",now());
        db.update("UPDATE delivery_package SET last_return_id=?,failed_return=?,version_no=version_no+1 WHERE id=?",ret,"DELIVERY_FAILED".equals(text(p,"ORDER_STATUS")),packageId);
        state.audit(actor,"REPORT_RETURN",packageId,"Return reported. Warehouse receipt, stock and custody are unchanged.");
        return "Return reported. Stock stays unchanged until Warehouse confirms actual receipt.";
    }

    private String confirmReturn(String actor,String packageId,Map<String,Object> body) {
        Map<String,Object> p=state.packageRow(packageId);String condition=requiredText(body,"condition",30);
        if(!Set.of("INTACT","DAMAGED").contains(condition))throw ApiException.bad("Choose Intact or Damaged.");
        Map<String,Object> receipt=state.latestReturn(p);
        if(!"AWAITING_WAREHOUSE".equals(text(receipt,"RECEIPT_STATUS")))throw ApiException.conflict("Warehouse receipt has already been recorded.");
        if(!"DELIVERY".equals(text(p,"CUSTODY")))throw ApiException.conflict("This package is not awaiting a physical return from Delivery.");
        boolean intact="INTACT".equals(condition);String returnId=text(receipt,"ID");
        // Decision U-06, B: The demo receives every ordered unit. Damaged units remain unavailable.
        // Place holder: Warehouse must supply actual line counts, condition evidence and discrepancy resolution.
        for(Map<String,Object> item:state.items(text(p,"ORDER_ID"))) {
            String sku=text(item,"SKU");int qty=number(item,"QUANTITY"),reserved=intact?qty:0,unusable=intact?0:qty;
            db.update("INSERT INTO return_line VALUES(?,?,?,?)",returnId,sku,qty,unusable);
            db.update("UPDATE product_stock SET physical_stock=physical_stock+?,reserved_stock=reserved_stock+?,unavailable_stock=unavailable_stock+? WHERE sku=?",qty,reserved,unusable,sku);
            db.update("UPDATE stock_allocation SET quantity=? WHERE package_id=? AND sku=?",reserved,packageId,sku);
            state.movement(sku,packageId,returnId,"CONFIRMED_RETURN",qty,reserved,unusable,"DEMO_WAREHOUSE");
        }
        db.update("UPDATE return_receipt SET receipt_status='RECEIVED',received_at=?,received_by='DEMO_WAREHOUSE',receipt_condition=? WHERE id=?",now(),condition,returnId);
        db.update("UPDATE delivery_package SET custody='WAREHOUSE',holder_id=NULL,hold_reason=?,version_no=version_no+1 WHERE id=?",intact?null:"DAMAGED_RETURN",packageId);
        state.custody(packageId,returnId,"WAREHOUSE_RECEIPT",text(p,"HOLDER_ID"),"WAREHOUSE","DEMO_WAREHOUSE");
        if(!intact)state.discrepancy(packageId,returnId,"DAMAGED_RETURN","DEMO_WAREHOUSE","Place holder: all demo units were received as unavailable. Warehouse/Admin must resolve the case.");
        if(intact&&!flag(p,"FAILED_RETURN")){
            // Decision U-10, B: A pre-attempt return does not invent a failed customer visit.
            state.setStatus(p,flag(p,"RETRY_APPROVED")?"READY_FOR_REDELIVERY":"READY_FOR_PICKUP");
            state.makeList(List.of(packageId));
        }
        state.audit("DEMO_WAREHOUSE","PLACE_HOLDER_CONFIRM_RETURN",packageId,"Place holder receipt simulation requested by "+actor+". Condition: "+condition);
        return "Place holder: Warehouse receipt simulated. "+(intact?(flag(p,"FAILED_RETURN")?"Client instruction and Admin approval are still required.":"A new pickup list is ready."):"Damaged units are held and unavailable; no redelivery was approved.");
    }

    private String instruction(String actor,String packageId,Map<String,Object> body) {
        Map<String,Object> p=state.packageRow(packageId);Map<String,Object> receipt=state.latestReturn(p);
        if(!"RECEIVED".equals(text(receipt,"RECEIPT_STATUS"))||!"WAREHOUSE".equals(text(p,"CUSTODY"))||!"DELIVERY_FAILED".equals(text(p,"ORDER_STATUS"))||!flag(p,"FAILED_RETURN"))
            throw ApiException.conflict("A returned failed delivery awaiting Admin review is required.");
        String choice=requiredText(body,"instruction",30);
        if(!Set.of("REDELIVER","CANCEL").contains(choice))throw ApiException.bad("Choose Redeliver or Cancel.");
        int version=db.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM client_instruction WHERE package_id=?",Integer.class,packageId);
        // Decision U-08, B: A changed instruction supersedes the old version before any approval.
        db.update("UPDATE client_instruction SET instruction_status='SUPERSEDED' WHERE package_id=? AND instruction_status='PENDING'",packageId);
        db.update("INSERT INTO client_instruction(id,package_id,return_id,detail_id,version_no,instruction_type,instruction_status,created_at) VALUES(?,?,?,?,?,?,?,?)",newId("INS"),packageId,text(receipt,"ID"),text(p,"DETAIL_ID"),version,choice,"PENDING",now());
        state.audit("DEMO_CLIENT","PLACE_HOLDER_INSTRUCTION",packageId,"Place holder client instruction simulation requested by "+actor+". Version "+version+": "+choice);
        return "Place holder: current Client instruction simulated. Admin approval is still required.";
    }

    private String decision(String actor,String packageId,Map<String,Object> body) {
        Map<String,Object> p=state.packageRow(packageId);Map<String,Object> receipt=state.latestReturn(p);
        if(!"RECEIVED".equals(text(receipt,"RECEIPT_STATUS"))||!"WAREHOUSE".equals(text(p,"CUSTODY"))||!"DELIVERY_FAILED".equals(text(p,"ORDER_STATUS")))throw ApiException.conflict("Admin review requires the latest confirmed failed-delivery return.");
        Map<String,Object> instruction=optionalRow(db,"SELECT * FROM client_instruction WHERE package_id=? AND return_id=? AND instruction_status='PENDING' ORDER BY version_no DESC LIMIT 1",packageId,text(receipt,"ID"));
        if(instruction==null)throw ApiException.conflict("A current Client instruction is required before Admin approval.");
        String choice=requiredText(body,"decision",40),instructionId=text(instruction,"ID");
        if("APPROVE_REDELIVERY".equals(choice)){
            if(!"REDELIVER".equals(text(instruction,"INSTRUCTION_TYPE"))||state.pendingCancellation(packageId))throw ApiException.conflict("A pending cancellation blocks redelivery. Use the current Client instruction.");
            if(text(p,"HOLD_REASON")!=null||!"INTACT".equals(text(receipt,"RECEIPT_CONDITION")))throw ApiException.conflict("Warehouse must resolve the damaged-goods hold before redelivery.");
            db.update("UPDATE client_instruction SET instruction_status='APPROVED',resolved_at=?,resolved_by='DEMO_ADMIN' WHERE id=?",now(),instructionId);
            db.update("UPDATE delivery_package SET retry_approved=TRUE,approved_instruction_id=?,detail_id=?,version_no=version_no+1 WHERE id=?",instructionId,text(instruction,"DETAIL_ID"),packageId);
            state.setStatus(p,"READY_FOR_REDELIVERY");state.makeList(List.of(packageId));
            state.audit("DEMO_ADMIN","PLACE_HOLDER_APPROVE_REDELIVERY",packageId,"Place holder approval requested by "+actor+" binds return, instruction and delivery-detail version.");
            return "Place holder: Admin redelivery approval simulated. Scan the new pickup list before starting delivery.";
        }
        if(!"APPROVE_CANCELLATION".equals(choice))throw ApiException.bad("Choose a valid Admin decision.");
        if(!"CANCEL".equals(text(instruction,"INSTRUCTION_TYPE")))throw ApiException.conflict("The current Client instruction does not request cancellation.");
        for(Map<String,Object> allocation:db.queryForList("SELECT * FROM stock_allocation WHERE package_id=?",packageId)){
            int quantity=number(allocation,"QUANTITY");if(quantity==0)continue;
            String sku=text(allocation,"SKU");
            db.update("UPDATE product_stock SET reserved_stock=reserved_stock-? WHERE sku=? AND reserved_stock>=?",quantity,sku,quantity);
            db.update("UPDATE stock_allocation SET quantity=0 WHERE package_id=? AND sku=?",packageId,sku);
            state.movement(sku,packageId,instructionId,"CANCEL_RELEASE",0,-quantity,0,"DEMO_ADMIN");
        }
        db.update("UPDATE client_instruction SET instruction_status='APPROVED',resolved_at=?,resolved_by='DEMO_ADMIN' WHERE id=?",now(),instructionId);
        db.update("UPDATE delivery_package SET retry_approved=FALSE,approved_instruction_id=NULL,hold_reason='CANCELLED',version_no=version_no+1 WHERE id=?",packageId);
        state.setStatus(p,"CANCELLED");
        db.update("UPDATE discrepancy_case SET case_status='CANCELLED_HELD_GOODS' WHERE package_id=? AND case_status='OPEN'",packageId);
        state.notify(actor,p,"ORDER_CANCELLED","Order cancelled. Place holder refund integration must resolve the full refund; no refund success is claimed.");
        state.audit("DEMO_ADMIN","PLACE_HOLDER_APPROVE_CANCELLATION",packageId,"Place holder approval requested by "+actor+". Unavailable stock remains unavailable; refund unresolved.");
        return "Place holder: Admin cancellation simulated. Cancellation is final; the refund service is a Place holder, not Refunded.";
    }

    private String now() {
        return clock.instant().toString();
    }
}
