package hk.edu.hkmu.lms.delivery.access;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;

@Service
public class DeliveryAccess {
    private final JdbcTemplate db;
    private final boolean demo;

    public DeliveryAccess(JdbcTemplate db, @Value("${app.demo-enabled:false}") boolean demo) {
        this.db = db;
        this.demo = demo;
    }

    public boolean demoEnabled() { return demo; }

    public String mode(){return demo?"demo":"integration";}
    public Map<String,Object> user(String actor) {
        Map<String,Object> account=requiredRow(db,"SELECT * FROM app_account WHERE id=?",actor);
        if(!"ACTIVE".equals(text(account,"ACCOUNT_STATUS"))||!"DELIVERY".equals(text(account,"ROLE")))throw ApiException.forbidden();
        return fields("id",actor,"displayName",text(account,"DISPLAY_NAME"),"role","DELIVERY");
    }
}
