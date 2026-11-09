package hk.edu.hkmu.lms.delivery.fulfilment;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;
import java.util.Set;

@Service
public class FulfilmentState {
    private final JdbcTemplate db;
    private final Clock clock;
    public FulfilmentState(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Map<String,Object> holder(String actor,String id) {
        Map<String,Object> p=packageRow(id);
        if(!"DELIVERY".equals(text(p,"CUSTODY"))||!actor.equals(text(p,"HOLDER_ID"))||!actor.equals(text(p,"RESPONSIBLE_ID")))throw ApiException.forbidden();
        return p;
    }
    public boolean eligible(Map<String,Object> p){return "WAREHOUSE".equals(text(p,"CUSTODY"))&&text(p,"HOLD_REASON")==null&&flag(p,"PAYMENT_ACCEPTED")&&Set.of("READY_FOR_PICKUP","READY_FOR_REDELIVERY").contains(text(p,"ORDER_STATUS"))&&(!"READY_FOR_REDELIVERY".equals(text(p,"ORDER_STATUS"))||flag(p,"RETRY_APPROVED"))&&!pendingReturn(text(p,"ID"))&&!pendingCancellation(text(p,"ID"));}
    public Map<String,Object> packageRow(String id){return requiredRow(db,"SELECT p.*,o.order_status,o.order_reference,o.client_id,o.payment_accepted FROM delivery_package p JOIN delivery_order o ON p.order_id=o.id WHERE p.id=?",id);}
    public List<Map<String,Object>> items(String orderId){return db.queryForList("SELECT i.*,s.product_name FROM order_item i JOIN product_stock s ON s.sku=i.sku WHERE i.order_id=? ORDER BY i.id",orderId);}
    public boolean pendingReturn(String id){return db.queryForObject("SELECT COUNT(*) FROM return_receipt WHERE package_id=? AND receipt_status='AWAITING_WAREHOUSE'",Integer.class,id)>0;}
    public boolean pendingCancellation(String id){return db.queryForObject("SELECT COUNT(*) FROM client_instruction WHERE package_id=? AND instruction_type='CANCEL' AND instruction_status='PENDING'",Integer.class,id)>0;}
    public Map<String,Object> latestReturn(Map<String,Object> p){if(text(p,"LAST_RETURN_ID")==null)throw ApiException.conflict("A recorded Warehouse return is required.");return requiredRow(db,"SELECT * FROM return_receipt WHERE id=? AND package_id=?",text(p,"LAST_RETURN_ID"),text(p,"ID"));}
    public void setStatus(Map<String,Object> p,String status){db.update("UPDATE delivery_order SET order_status=? WHERE id=?",status,text(p,"ORDER_ID"));}
    public String makeList(List<String> packages){
        if(packages.isEmpty())return null;
        String list=newId("PL");
        for(String pkg:packages)if(db.queryForObject("SELECT COUNT(*) FROM pickup_item i JOIN pickup_list l ON l.id=i.list_id WHERE i.package_id=? AND l.list_status<>'CLOSED' AND i.collection_outcome IN ('PENDING','CANNOT_COLLECT')",Integer.class,pkg)>0)throw ApiException.conflict("The package is still on an active pickup list.");
        db.update("INSERT INTO pickup_list(id,qr_code,list_status,created_at,created_by) VALUES(?,?,?,?,?)",list,list,"OPEN",now(),"DEMO_WAREHOUSE");
        for(String pkg:packages)db.update("INSERT INTO pickup_item(list_id,package_id,collection_outcome) VALUES(?,?,?)",list,pkg,"PENDING");
        return list;
    }
    public void movement(String sku,String pkg,String source,String type,int physical,int reserved,int unavailable,String actor){db.update("INSERT INTO stock_movement VALUES(?,?,?,?,?,?,?,?,?,?)",newId("MOV"),sku,pkg,source,type,physical,reserved,unavailable,actor,now());}
    public void custody(String pkg,String source,String type,String from,String to,String actor){db.update("INSERT INTO custody_event VALUES(?,?,?,?,?,?,?,?)",newId("CUS"),pkg,source,type,from,to,actor,now());}
    public void audit(String actor,String action,String target,String detail){db.update("INSERT INTO audit_event VALUES(?,?,?,?,?,?)",newId("AUD"),actor,action,target,detail,now());}
    public void notify(String actor,Map<String,Object> p,String type,String message){db.update("INSERT INTO notification_outbox(id,actor_id,client_id,package_id,event_type,safe_message,created_at) VALUES(?,?,?,?,?,?,?)",newId("N"),actor,text(p,"CLIENT_ID"),text(p,"ID"),type,message,now());}
    public void discrepancy(String pkg,String source,String condition,String actor,String detail){db.update("INSERT INTO discrepancy_case VALUES(?,?,?,?,?,?,?,?)",newId("CASE"),pkg,source,"OPEN",condition,actor,now(),detail);}

    private String now() {
        return clock.instant().toString();
    }
}
