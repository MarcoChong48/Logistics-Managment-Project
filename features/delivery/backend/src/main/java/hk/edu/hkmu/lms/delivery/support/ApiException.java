package hk.edu.hkmu.lms.delivery.support;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }

    public static ApiException conflict(String message) { return new ApiException(409,"STATE_CONFLICT",message); }
    public static ApiException bad(String message) { return new ApiException(400,"INVALID_INPUT",message); }
    public static ApiException forbidden() { return new ApiException(403,"ACCESS_DENIED","This action is not available for your current work."); }
}
