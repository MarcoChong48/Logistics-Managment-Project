package hk.edu.hkmu.lms.delivery.access;

import hk.edu.hkmu.lms.delivery.support.Rows;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthenticationProvider localAccounts(JdbcTemplate jdbc, PlatformTransactionManager manager, PasswordEncoder encoder, Clock clock) {
        // Place holder: The shared Auth owner must provide account lifecycle and session integration.
        TransactionTemplate tx = new TransactionTemplate(manager);
        String unknownHash = encoder.encode("not-a-login-password");
        return new AuthenticationProvider() {
            @Override public Authentication authenticate(Authentication input) {
                String username = input.getName() == null ? "" : input.getName().toLowerCase(java.util.Locale.ROOT);
                String password = input.getCredentials() == null ? "" : input.getCredentials().toString();
                Boolean ok = tx.execute(status -> {
                    Rows.lock(jdbc);
                    List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM app_account WHERE id=?", username);
                    if (rows.isEmpty()) { encoder.matches(password, unknownHash); return false; }
                    Map<String,Object> row = rows.getFirst();
                    Instant now = clock.instant();
                    String until = (String)row.get("LOCK_UNTIL");
                    if (!"ACTIVE".equals(row.get("ACCOUNT_STATUS")) || (until != null && now.isBefore(Instant.parse(until)))) return false;
                    int count = ((Number)row.get("FAILED_COUNT")).intValue();
                    if (until != null) count=0;
                    if (encoder.matches(password, (String)row.get("PASSWORD_HASH"))) {
                        jdbc.update("UPDATE app_account SET failed_count=0,lock_until=NULL WHERE id=?",username);
                        return true;
                    }
                    count++;
                    jdbc.update("UPDATE app_account SET failed_count=?,lock_until=? WHERE id=?",count,count>=5?now.plus(Duration.ofMinutes(15)).toString():null,username);
                    return false;
                });
                if (!Boolean.TRUE.equals(ok)) throw new BadCredentialsException("Sign-in failed.");
                return new UsernamePasswordAuthenticationToken(username,null,List.of(new SimpleGrantedAuthority("ROLE_DELIVERY")));
            }
            @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json, JdbcTemplate jdbc, Clock clock, AuthenticationProvider localAccounts) throws Exception {
        HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http.authenticationProvider(localAccounts)
            .csrf(config -> config.csrfTokenRepository(csrf))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/api/session", "/api/login", "/actuator/health", "/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,ex) -> write(json,res,401,"AUTH_REQUIRED","Sign in to continue."))
                .accessDeniedHandler((req,res,ex) -> write(json,res,403,"ACCESS_DENIED","Your session or security token changed. Refresh and try again.")))
            .formLogin(form -> form.loginProcessingUrl("/api/login")
                .successHandler((req,res,auth) -> {
                    securityEvent(jdbc,clock,req,auth.getName(),"LOGIN","SUCCESS");
                    res.setContentType("application/json"); json.writeValue(res.getOutputStream(),Map.of("message","Signed in."));
                })
                .failureHandler((req,res,ex) -> {
                    String name=req.getParameter("username");
                    String known = name != null && jdbc.queryForObject("SELECT COUNT(*) FROM app_account WHERE id=?",Integer.class,name.toLowerCase(java.util.Locale.ROOT))>0?name.toLowerCase(java.util.Locale.ROOT):null;
                    securityEvent(jdbc,clock,req,known,"LOGIN","FAILED");
                    write(json,res,401,"SIGN_IN_FAILED","Sign-in failed. Check your details, or try later if the account is locked.");
                }))
            .logout(logout -> logout.logoutUrl("/api/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> {
                    securityEvent(jdbc,clock,req,auth==null?null:auth.getName(),"LOGOUT","SUCCESS");
                    res.setContentType("application/json");json.writeValue(res.getOutputStream(),Map.of("message","Signed out."));
                }));
        return http.build();
    }

    private static void write(ObjectMapper json,HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        json.writeValue(response.getOutputStream(),Map.of("code",code,"message",message));
    }
    private static void securityEvent(JdbcTemplate jdbc,Clock clock,HttpServletRequest req,String user,String type,String result) {
        String device=req.getHeader("User-Agent"); if(device!=null&&device.length()>300)device=device.substring(0,300);
        jdbc.update("INSERT INTO security_event(id,account_id,event_type,event_result,occurred_at,ip_address,device_info) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),user,type,result,clock.instant().toString(),req.getRemoteAddr(),device);
    }
}
