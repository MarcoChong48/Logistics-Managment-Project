package hk.edu.hkmu.lms.delivery.attempts;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import hk.edu.hkmu.lms.delivery.support.ApiException;
import static hk.edu.hkmu.lms.delivery.support.Rows.*;
import static hk.edu.hkmu.lms.delivery.support.Fields.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import hk.edu.hkmu.lms.delivery.fulfilment.FulfilmentState;
import hk.edu.hkmu.lms.delivery.operations.DeliveryTransactions;

@Service
public class DeliveryAttempts {
    private final JdbcTemplate db;
    private final Clock clock;
    private final DeliveryTransactions transactions;
    private final FulfilmentState state;
    private final Path proofDirectory;
    private final int proofLimit;

    public DeliveryAttempts(JdbcTemplate db, Clock clock, DeliveryTransactions transactions,
                            FulfilmentState state,
                            @Value("${app.data-dir:./data}") String dataDir,
                            @Value("${app.photo-limit-bytes:5242880}") int proofLimit) {
        this.db = db;
        this.clock = clock;
        this.transactions = transactions;
        this.state = state;
        this.proofDirectory = Path.of(dataDir).toAbsolutePath().normalize().resolve("proofs");
        this.proofLimit = proofLimit;
    }

    public Map<String, Object> start(String actor, String packageId, Map<String, Object> body) {
        return transactions.command(actor, "START", packageId, body, () -> startAttempt(actor, packageId));
    }

    public Map<String, Object> outcome(String actor, String packageId, Map<String, Object> body) {
        return transactions.command(actor, "OUTCOME", packageId, body, () -> recordOutcome(actor, packageId, body));
    }

    private String startAttempt(String actor,String packageId) {
        Map<String,Object> p=state.holder(actor,packageId);
        if(!"SHIPPED".equals(text(p,"ORDER_STATUS"))||state.pendingReturn(packageId))throw ApiException.conflict("Collect this package and resolve any pending return before starting delivery.");
        if(optionalRow(db,"SELECT id FROM delivery_attempt WHERE package_id=? AND outcome='IN_PROGRESS'",packageId)!=null)throw ApiException.conflict("A delivery attempt is already active.");
        String attempt=newId("ATT");
        db.update("INSERT INTO delivery_attempt(id,package_id,worker_id,pickup_id,detail_id,started_at,outcome) VALUES(?,?,?,?,?,?,?)",attempt,packageId,actor,text(p,"LAST_PICKUP_ID"),text(p,"DETAIL_ID"),now(),"IN_PROGRESS");
        state.setStatus(p,"OUT_FOR_DELIVERY");state.audit(actor,"START_DELIVERY",packageId,"Attempt "+attempt+" uses the frozen delivery-detail version.");
        return "Delivery started for this order only.";
    }

    private String recordOutcome(String actor,String packageId,Map<String,Object> body) {
        Map<String,Object> p=state.holder(actor,packageId);
        Map<String,Object> attempt=activeAttempt(actor,p);
        String choice=requiredText(body,"outcome",30),reason=optionalText(body,"reason",40),note=optionalText(body,"note",600);
        String attemptId=text(attempt,"ID");
        if("DELIVERED".equals(choice)) {
            Object input=body.get("proofIds");
            if(!(input instanceof List<?> ids)||ids.isEmpty()||ids.size()>5)throw ApiException.bad("Attach at least one saved signature or photo before marking Delivered.");
            for(Object proofId:ids) {
                if(!(proofId instanceof String key))throw ApiException.bad("Check the proof selection.");
                Map<String,Object> proof=requiredRow(db,"SELECT * FROM delivery_proof WHERE id=?",key);
                if(!attemptId.equals(text(proof,"ATTEMPT_ID"))||!actor.equals(text(proof,"WORKER_ID")))throw ApiException.forbidden();
                if(!Files.isRegularFile(proofPath(text(proof,"FILE_NAME"))))throw ApiException.conflict("A proof file is unavailable. Upload it again before completing delivery.");
            }
            for(Object proofId:ids)db.update("UPDATE delivery_proof SET used_for_outcome=TRUE WHERE id=?",proofId);
            db.update("UPDATE delivery_attempt SET outcome='DELIVERED',ended_at=? WHERE id=?",now(),attemptId);
            state.setStatus(p,"DELIVERED");
            db.update("UPDATE delivery_package SET custody='RECIPIENT',holder_id=NULL,version_no=version_no+1 WHERE id=?",packageId);
            state.custody(packageId,attemptId,"DELIVERED",actor,"RECIPIENT",actor);
            state.notify(actor,p,"DELIVERED","Delivery completed. Place holder notification transport must notify the Client.");
        } else if("FAILED".equals(choice)) {
            if(!Set.of("CLIENT_NOT_HOME","WRONG_ADDRESS","CLIENT_REFUSED","UNABLE_TO_CONTACT","OTHER").contains(reason))throw ApiException.bad("Choose a delivery failure reason.");
            if("OTHER".equals(reason)&&note.isBlank())throw ApiException.bad("Add a note for Other.");
            db.update("UPDATE delivery_attempt SET outcome='FAILED',ended_at=?,reason=?,internal_note=? WHERE id=?",now(),reason,note,attemptId);
            state.setStatus(p,"DELIVERY_FAILED");
            db.update("UPDATE delivery_package SET retry_approved=FALSE,approved_instruction_id=NULL,version_no=version_no+1 WHERE id=?",packageId);
            state.notify(actor,p,"DELIVERY_FAILED","Delivery failed: "+reason+". Place holder transport must send only this standard reason to the Client.");
        }else throw ApiException.bad("Choose Delivered or Failed.");
        state.audit(actor,"DELIVERY_OUTCOME",packageId,"Attempt "+attemptId+" outcome: "+choice+". Internal notes excluded from audit.");
        return "DELIVERED".equals(choice)?"Delivery completed with saved proof.":"Failed attempt saved. Return the package for Warehouse confirmation.";
    }
    private Map<String,Object> activeAttempt(String actor,Map<String,Object> p) {
        if(!"OUT_FOR_DELIVERY".equals(text(p,"ORDER_STATUS"))||state.pendingReturn(text(p,"ID")))throw ApiException.conflict("Start the current delivery before submitting proof or an outcome.");
        Map<String,Object> attempt=optionalRow(db,"SELECT * FROM delivery_attempt WHERE package_id=? AND worker_id=? AND outcome='IN_PROGRESS'",text(p,"ID"),actor);
        if(attempt==null||!Objects.equals(text(p,"LAST_PICKUP_ID"),text(attempt,"PICKUP_ID")))throw ApiException.conflict("No current delivery attempt is active.");
        return attempt;
    }

    public record ProofContent(byte[] bytes,String contentType){}
    private record CleanImage(byte[] bytes,String contentType,String suffix){}

    public Map<String, Object> upload(String actor, String packageId, String type,
                                       MultipartFile file, String requestId) {
        return transactions.withWorker(actor, () -> {
            if(!Set.of("PHOTO","SIGNATURE").contains(type))throw ApiException.bad("Choose Photo or Signature.");
            if(file.isEmpty()||file.getSize()>proofLimit)throw ApiException.bad("Choose a non-empty image no larger than 5 MB.");
            byte[] input;
            try{input=file.getBytes();}catch(IOException ex){throw ApiException.bad("The image could not be read. Try again.");}

            String key = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
            Map<String, Object> payload = fields("type", type, "sha256", DeliveryTransactions.digest(input));
            return transactions.once(actor, "PROOF:" + packageId, key, payload,
                () -> saveProof(actor, packageId, type, input));
        });
    }

    private Map<String, Object> saveProof(String actor, String packageId, String type, byte[] input) {
        Map<String, Object> p = state.holder(actor, packageId);
        Map<String,Object> attempt=activeAttempt(actor,p);
        if(db.queryForObject("SELECT COUNT(*) FROM delivery_proof WHERE attempt_id=?",Integer.class,text(attempt,"ID"))>=10)throw ApiException.bad("This attempt already has enough saved images. Use one of its saved proofs.");
        CleanImage image=cleanImage(input,type);
        String proofId=newId("PROOF"),fileName=UUID.randomUUID()+image.suffix();
        // Decision U-09, B: Keep objects private. A file must exist before an outcome can commit.
        // Place holder: The Storage owner replaces this local adapter with private object storage and credentials.
        try{Files.createDirectories(proofDirectory);Files.write(proofPath(fileName),image.bytes(),StandardOpenOption.CREATE_NEW);}catch(IOException ex){throw new ApiException(409,"PROOF_STORAGE_UNAVAILABLE","Proof could not be saved. Keep your image and try again.");}
        db.update("INSERT INTO delivery_proof(id,attempt_id,worker_id,proof_type,file_name,content_type,byte_count,sha256,created_at) VALUES(?,?,?,?,?,?,?,?,?)",proofId,text(attempt,"ID"),actor,type,fileName,image.contentType(),image.bytes().length,DeliveryTransactions.digest(image.bytes()),now());
        Map<String, Object> result = fields("id", proofId);
        state.audit(actor,"UPLOAD_PROOF",packageId,"Private proof saved for the active attempt. No image or contact data copied into audit.");
        return result;
    }

    private CleanImage cleanImage(byte[] input,String type) {
        try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if(stream==null)throw ApiException.bad("Choose a valid JPEG or PNG image.");
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw ApiException.bad("The file is not a readable JPEG or PNG image.");
            ImageReader reader=readers.next();
            try{
                reader.setInput(stream,true,true);
                String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!Set.of("png","jpeg","jpg").contains(format)||("SIGNATURE".equals(type)&&!"png".equals(format)))throw ApiException.bad("Photos need JPEG or PNG. Signatures need PNG.");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<16||height<16||(long)width*height>20_000_000)throw ApiException.bad("Use an image between 16 pixels and 20 million pixels.");
                BufferedImage image=reader.read(0);
                if(image==null||!hasVisibleContent(image))throw ApiException.bad("The image is blank. Capture a visible signature or photo.");
                // Re-encode decoded pixels to remove untrusted metadata and appended file content.
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                boolean png="png".equals(format);
                if(!ImageIO.write(image,png?"png":"jpg",output))throw ApiException.bad("The image could not be saved in a supported format.");
                byte[] clean=output.toByteArray();if(clean.length>proofLimit)throw ApiException.bad("The decoded image is too large. Choose a smaller image.");
                return new CleanImage(clean,png?"image/png":"image/jpeg",png?".png":".jpg");
            }finally{reader.dispose();}
        }catch(ApiException ex){throw ex;}catch(IOException|RuntimeException ex){throw ApiException.bad("The image is damaged or unreadable. Choose another JPEG or PNG.");}
    }
    private static boolean hasVisibleContent(BufferedImage image) {
        Integer first=null;int visible=0;boolean varied=false;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int pixel=image.getRGB(x,y);if((pixel>>>24)<16)continue;
            visible++;if(first==null)first=pixel;else if(pixel!=first)varied=true;
            if(visible>=16&&varied)return true;
        }
        // A solid opaque signature on a transparent canvas can still have visible strokes.
        return visible>=16&&visible<(long)image.getWidth()*image.getHeight()/2;
    }
    private Path proofPath(String fileName){
        Path file=proofDirectory.resolve(fileName).normalize();
        if(!proofDirectory.equals(file.getParent()))throw ApiException.forbidden();return file;
    }

    public ProofContent proof(String actor, String proofId) {
        return transactions.withWorker(actor, () -> readProof(actor, proofId));
    }

    private ProofContent readProof(String actor, String proofId) {
        Map<String,Object> proof=requiredRow(db,"SELECT * FROM delivery_proof WHERE id=?",proofId);
        if(!actor.equals(text(proof,"WORKER_ID")))throw ApiException.forbidden();
        Map<String,Object> attempt=requiredRow(db,"SELECT * FROM delivery_attempt WHERE id=?",text(proof,"ATTEMPT_ID"));
        Map<String,Object> p=state.holder(actor,text(attempt,"PACKAGE_ID"));
        if(!"IN_PROGRESS".equals(text(attempt,"OUTCOME"))||!"OUT_FOR_DELIVERY".equals(text(p,"ORDER_STATUS")))throw ApiException.forbidden();
        try{return new ProofContent(Files.readAllBytes(proofPath(text(proof,"FILE_NAME"))),text(proof,"CONTENT_TYPE"));}catch(IOException ex){throw new ApiException(404,"PROOF_UNAVAILABLE","This private proof file is unavailable.");}
    }

    @Scheduled(fixedDelayString="${app.maintenance-delay-ms:3600000}",initialDelayString="${app.maintenance-delay-ms:3600000}")
    @Transactional
    public void maintenance(){
        lock(db);int removedProofs=0,removedAttempts=0,orphans=0;String error=null;
        Instant yearAgo=clock.instant().minus(Duration.ofDays(365));
        // Decision U-09, B: Keep a minimal three-year summary, never an expired proof or private note.
        for(Map<String,Object> attempt:db.queryForList("SELECT * FROM delivery_attempt WHERE outcome<>'IN_PROGRESS' AND ended_at<?",yearAgo.toString())){
            String attemptId=text(attempt,"ID");boolean deleted=true;
            for(Map<String,Object> proof:db.queryForList("SELECT * FROM delivery_proof WHERE attempt_id=?",attemptId)){
                try{Files.deleteIfExists(proofPath(text(proof,"FILE_NAME")));removedProofs++;}catch(IOException ex){deleted=false;error="Private proof cleanup needs attention.";}
            }
            if(!deleted)continue;
            Instant ended=Instant.parse(text(attempt,"ENDED_AT"));
            if(ended.plus(Duration.ofDays(365*3L)).isAfter(clock.instant())&&rowCount(db,"SELECT COUNT(*) FROM retained_summary WHERE id=?",attemptId)==0)
                db.update("INSERT INTO retained_summary VALUES(?,?,?,?,?,?)",attemptId,text(attempt,"PACKAGE_ID"),text(attempt,"WORKER_ID"),text(attempt,"OUTCOME"),text(attempt,"ENDED_AT"),ended.plus(Duration.ofDays(365*3L)).toString());
            db.update("DELETE FROM delivery_proof WHERE attempt_id=?",attemptId);
            db.update("UPDATE return_receipt SET attempt_id=NULL WHERE attempt_id=?",attemptId);
            db.update("DELETE FROM delivery_attempt WHERE id=?",attemptId);removedAttempts++;
        }
        for(Map<String,Object> proof:db.queryForList("SELECT p.* FROM delivery_proof p JOIN delivery_attempt a ON a.id=p.attempt_id WHERE p.used_for_outcome=FALSE AND a.outcome<>'IN_PROGRESS' AND p.created_at<?",clock.instant().minus(Duration.ofDays(1)).toString())){
            try{Files.deleteIfExists(proofPath(text(proof,"FILE_NAME")));db.update("DELETE FROM delivery_proof WHERE id=?",text(proof,"ID"));removedProofs++;}catch(IOException ex){error="Unused proof cleanup needs attention.";}
        }
        if(Files.isDirectory(proofDirectory))try(var files=Files.list(proofDirectory)){
            for(Path path:files.filter(Files::isRegularFile).toList())if(rowCount(db,"SELECT COUNT(*) FROM delivery_proof WHERE file_name=?",path.getFileName().toString())==0&&Files.getLastModifiedTime(path).toInstant().isBefore(clock.instant().minus(Duration.ofDays(1)))){Files.delete(path);orphans++;}
        }catch(IOException ex){error="Orphan file cleanup needs attention.";}
        db.update("DELETE FROM retained_summary WHERE expires_at<?",now());
        db.update("DELETE FROM security_event WHERE occurred_at<?",clock.instant().minus(Duration.ofDays(90)).toString());
        db.update("DELETE FROM audit_event WHERE created_at<?",clock.instant().minus(Duration.ofDays(365*3L)).toString());
        db.update("DELETE FROM notification_outbox WHERE created_at<?",clock.instant().minus(Duration.ofDays(365*3L)).toString());
        db.update("MERGE INTO maintenance_status(id,last_run_at,removed_proofs,removed_attempts,orphan_count,last_error) KEY(id) VALUES(?,?,?,?,?,?)","proof-retention",now(),removedProofs,removedAttempts,orphans,error);
    }

    private String now() {
        return clock.instant().toString();
    }
}
