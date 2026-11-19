package hk.edu.hkmu.lms.delivery.access;

import hk.edu.hkmu.lms.delivery.access.AccountSecurity.PasswordRevision;
import hk.edu.hkmu.lms.delivery.support.Rows;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class SecurityConfig {
    static final String SESSION_TIMING_ATTRIBUTE = SecurityConfig.class.getName() + ".sessionTiming";
    private static final String SESSION_EXPIRY_HEADER = "X-Session-Expires-At";
    private static final String SERVER_TIME_HEADER = "X-Server-Time";

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthenticationProvider localAccounts(JdbcTemplate jdbc, PlatformTransactionManager manager, PasswordEncoder encoder, Clock clock) {
        // Place holder: The shared Auth owner must provide account lifecycle and session integration.
        TransactionTemplate tx = new TransactionTemplate(manager);
        String unknownHash = encoder.encode("not-a-login-password");
        return new AuthenticationProvider() {
            @Override public Authentication authenticate(Authentication input) {
                String username = input.getName().toLowerCase(Locale.ROOT);
                String password = input.getCredentials() == null ? "" : input.getCredentials().toString();
                Authentication authenticated = tx.execute(status -> {
                    Rows.lock(jdbc);
                    Map<String,Object> row = accountByUsername(jdbc, username);
                    if (row == null) {
                        AccountSecurity.matches(password, unknownHash, encoder);
                        return null;
                    }
                    Instant now = clock.instant();
                    String until = Rows.text(row, "LOCK_UNTIL");
                    if (!"DELIVERY".equals(Rows.text(row, "ROLE")) ||
                            !"ACTIVE".equals(Rows.text(row, "ACCOUNT_STATUS")) ||
                            (until != null && now.isBefore(Instant.parse(until)))) return null;

                    String accountId = Rows.text(row, "ID");
                    int count = until == null ? Rows.number(row, "FAILED_COUNT") : 0;
                    String passwordHash = Rows.text(row, "PASSWORD_HASH");
                    if (AccountSecurity.matches(password, passwordHash, encoder)) {
                        jdbc.update("UPDATE app_account SET failed_count=0,lock_until=NULL WHERE id=?", accountId);
                        var token = UsernamePasswordAuthenticationToken.authenticated(accountId, null,
                            List.of(new SimpleGrantedAuthority("ROLE_DELIVERY")));
                        token.setDetails(PasswordRevision.of(passwordHash));
                        return token;
                    }
                    count++;
                    jdbc.update("UPDATE app_account SET failed_count=?,lock_until=? WHERE id=?",
                        count, count>=5 ? now.plus(Duration.ofMinutes(15)).toString() : null, accountId);
                    return null;
                });
                if (authenticated == null) throw new BadCredentialsException("Sign-in failed.");
                return authenticated;
            }
            @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
        };
    }

    private static Map<String,Object> accountByUsername(JdbcTemplate jdbc, String username) {
        return Rows.optionalRow(jdbc, "SELECT * FROM app_account WHERE LOWER(username)=?", username);
    }

    @Bean SecurityFilterChain security(HttpSecurity http, JsonMapper json, JdbcTemplate jdbc, Clock clock,
                                       AuthenticationProvider localAccounts, AccountSecurity accounts) throws Exception {
        HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http.authenticationProvider(localAccounts)
            .addFilterAfter(sessionActivity(accounts, clock, json), SecurityContextHolderFilter.class)
            .csrf(config -> config.csrfTokenRepository(csrf))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/api/session", "/api/login", "/actuator/health", "/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,ex) -> write(json,res,401,"AUTH_REQUIRED","Sign in to continue."))
                .accessDeniedHandler((req,res,ex) -> write(json,res,403,"ACCESS_DENIED","Your session or security token changed. Refresh and try again.")))
            .formLogin(form -> form.loginProcessingUrl("/api/login")
                .successHandler((req,res,auth) -> {
                    accounts.securityEvent(req,auth.getName(),"LOGIN","SUCCESS");
                    setSessionTiming(req, res, clock.millis());
                    res.setContentType("application/json"); json.writeValue(res.getOutputStream(),Map.of("message","Signed in."));
                })
                .failureHandler((req,res,ex) -> {
                    String name=req.getParameter("username");
                    Map<String,Object> account = name == null ? null : accountByUsername(jdbc, name.toLowerCase(Locale.ROOT));
                    String known = account == null ? null : Rows.text(account, "ID");
                    accounts.securityEvent(req,known,"LOGIN","FAILED");
                    write(json,res,401,"SIGN_IN_FAILED","Sign-in failed. Check your details, or try later if the account is locked.");
                }))
            .logout(logout -> logout.logoutUrl("/api/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> {
                    accounts.securityEvent(req,auth==null?null:auth.getName(),"LOGOUT","SUCCESS");
                    res.setContentType("application/json");json.writeValue(res.getOutputStream(),Map.of("message","Signed out."));
                }));
        return http.build();
    }

    private static OncePerRequestFilter sessionActivity(AccountSecurity accounts, Clock clock, JsonMapper json) {
        return new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                      FilterChain chain) throws ServletException, IOException {
                String api = request.getContextPath() + "/api/";
                String path = request.getRequestURI();
                if (path.startsWith(api)) {
                    long requestTime = clock.millis();
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.isAuthenticated()) {
                        if (!(auth.getDetails() instanceof PasswordRevision revision)
                                || !accounts.isCurrentPassword(auth.getName(), revision)) {
                            logoutSession(request, response, auth);
                            if (!path.equals(api + "session")) {
                                write(json, response, 401, "AUTH_REQUIRED", "Your session ended. Sign in again.");
                                return;
                            }
                        } else if (!path.equals(api + "logout") && !path.equals(api + "change-password")) {
                            setSessionTiming(request, response, requestTime);
                        }
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }

    private static void setSessionTiming(HttpServletRequest request, HttpServletResponse response, long requestTime) {
        var session = request.getSession(false);
        if (session == null || session.getMaxInactiveInterval() <= 0) return;
        long expiresAt = requestTime + Duration.ofSeconds(session.getMaxInactiveInterval()).toMillis();
        request.setAttribute(SESSION_TIMING_ATTRIBUTE, new SessionTiming(requestTime, expiresAt));
        response.setHeader(SESSION_EXPIRY_HEADER, Long.toString(expiresAt));
        response.setHeader(SERVER_TIME_HEADER, Long.toString(requestTime));
    }

    static void logoutSession(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    record SessionTiming(long serverTime, long expiresAt) {}

    private static void write(JsonMapper json,HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        json.writeValue(response.getOutputStream(),Map.of("code",code,"message",message));
    }
}
