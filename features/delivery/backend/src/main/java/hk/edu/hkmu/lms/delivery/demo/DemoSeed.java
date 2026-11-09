package hk.edu.hkmu.lms.delivery.demo;

import hk.edu.hkmu.lms.delivery.support.Rows;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoSeed implements ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final boolean enabled;
    DemoSeed(JdbcTemplate db,PasswordEncoder encoder,Clock clock,@Value("${app.demo-enabled:false}") boolean enabled) { this.db=db;this.encoder=encoder;this.clock=clock;this.enabled=enabled; }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(!enabled)return;
        Rows.lock(db);
        // Place holder: This local seed replaces Auth, Client, Warehouse and payment eligibility feeds.
        // Never seed again over saved work. It is a demo, not a production account setup.
        if(db.queryForObject("SELECT COUNT(*) FROM app_account",Integer.class)>0)return;
        String password=encoder.encode("Delivery123!");
        db.update("INSERT INTO app_account(id,display_name,password_hash,role,account_status) VALUES(?,?,?,?,?)","ken","Ken Tanaka",password,"DELIVERY","ACTIVE");
        db.update("INSERT INTO app_account(id,display_name,password_hash,role,account_status) VALUES(?,?,?,?,?)","mei","Mei Wong",password,"DELIVERY","ACTIVE");
        db.update("INSERT INTO product_stock VALUES(?,?,?,?,?,?)","MUG-01","Travel mug",40,3,0,5);
        db.update("INSERT INTO product_stock VALUES(?,?,?,?,?,?)","NOTE-01","Notebook",60,3,0,5);
        db.update("INSERT INTO product_stock VALUES(?,?,?,?,?,?)","BAG-01","Canvas bag",30,1,0,5);
        seedPackage("1001","Demo Recipient One","+852 6123 4567","Demo address: Kowloon, Hong Kong",List.of(new Item("MUG-01",2),new Item("NOTE-01",1)));
        seedPackage("1002","Demo Recipient Two","+852 6234 5678","Demo address: Sha Tin, Hong Kong",List.of(new Item("NOTE-01",2)));
        seedPackage("1003","Demo Recipient Three","+852 6345 6789","Demo address: Tsuen Wan, Hong Kong",List.of(new Item("MUG-01",1),new Item("BAG-01",1)));
        String now=clock.instant().toString();
        db.update("INSERT INTO pickup_list(id,qr_code,list_status,created_at,created_by) VALUES(?,?,?,?,?)","PL-1001","PL-1001","OPEN",now,"DEMO_WAREHOUSE");
        db.update("INSERT INTO pickup_list(id,qr_code,list_status,created_at,created_by) VALUES(?,?,?,?,?)","PL-1002","PL-1002","OPEN",now,"DEMO_WAREHOUSE");
        db.update("INSERT INTO pickup_item(list_id,package_id,collection_outcome) VALUES(?,?,?)","PL-1001","PKG-1001","PENDING");
        db.update("INSERT INTO pickup_item(list_id,package_id,collection_outcome) VALUES(?,?,?)","PL-1001","PKG-1002","PENDING");
        db.update("INSERT INTO pickup_item(list_id,package_id,collection_outcome) VALUES(?,?,?)","PL-1002","PKG-1003","PENDING");
    }
    private record Item(String sku,int quantity){}
    private void seedPackage(String number,String name,String phone,String address,List<Item> items) {
        String order="ORD-"+number,pkg="PKG-"+number,detail="DETAIL-"+number;
        db.update("INSERT INTO delivery_order VALUES(?,?,?,?,?,?)",order,"LMS-"+number,"DEMO_CLIENT_"+number,"READY_FOR_PICKUP",true,clock.instant().toString());
        db.update("INSERT INTO delivery_detail VALUES(?,?,?,?,?,?)",detail,order,1,name,phone,address);
        db.update("INSERT INTO delivery_package(id,order_id,qr_code,custody,detail_id) VALUES(?,?,?,?,?)",pkg,order,pkg,"WAREHOUSE",detail);
        int i=1;for(Item item:items) {
            db.update("INSERT INTO order_item VALUES(?,?,?,?)","ITEM-"+number+"-"+(i++),order,item.sku(),item.quantity());
            db.update("INSERT INTO stock_allocation VALUES(?,?,?)",pkg,item.sku(),item.quantity());
        }
    }
}
