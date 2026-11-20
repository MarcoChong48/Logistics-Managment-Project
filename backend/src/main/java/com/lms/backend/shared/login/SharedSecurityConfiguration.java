package com.lms.backend.shared.login;

import java.util.function.Supplier;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedSecurityEvent;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.value.SharedUserRole;

@Configuration
@EnableWebSecurity
public class SharedSecurityConfiguration {

    // ===== SECTION 1: PASSWORD HASHER =====

    /**
     * What it does: Makes the same BCrypt encoder available to login, bootstrap and account tools.
     * Input: None.
     * Output: The BCrypt encoder.
     */
    @Bean
    PasswordEncoder sharedEncodePassword() {
        return new BCryptPasswordEncoder();
    }

    // ===== SECTION 2: OPEN AND PROTECTED WEB ADDRESSES =====

    /**
     * What it does: Applies the shared session and role rules before a route can run.
     * Input: The security builder, database, security log writer, and shared web error resolver.
     * Output: The security filter chain.
     */
    @Bean
    SecurityFilterChain sharedConfigureSecurity(HttpSecurity sharedConfigureSecurityBuilder,
            AppUserRepository sharedConfigureSecurityAppUserRepository,
            SharedSecurityLogWriter sharedConfigureSecurityLogWriter,
            SharedCurrentUserReader sharedConfigureSecurityCurrentUserReader,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver sharedConfigureSecurityErrorResolver)
            throws Exception {
        // Requests go through the same path rules for HTML pages and their API calls.
        sharedConfigureSecurityBuilder.authorizeHttpRequests(sharedConfigureSecurityRequests ->
                sharedConfigureSecurityRequests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(SharedSecurityConfiguration::sharedCheckOpenWebAddress).permitAll()
                        .requestMatchers("/admin", "/admin/**", "/api/admin", "/api/admin/**")
                        .hasRole(SharedUserRole.ADMIN.name())
                        .requestMatchers("/client", "/client/**", "/api/client", "/api/client/**")
                        .hasRole(SharedUserRole.CLIENT.name())
                        .requestMatchers("/warehouse", "/warehouse/**", "/api/warehouse", "/api/warehouse/**")
                        .hasRole(SharedUserRole.WAREHOUSE_WORKER.name())
                        .requestMatchers("/delivery", "/delivery/**", "/api/delivery", "/api/delivery/**")
                        .hasRole(SharedUserRole.DELIVERY_WORKER.name())
                        .anyRequest().authenticated());

        // Anonymous sessions can obtain a token. Unsafe requests must carry it as a header.
        sharedConfigureSecurityToken(sharedConfigureSecurityBuilder);

        // The servlet session owns identity. Rebuild Spring's authentication on each protected request.
        sharedConfigureSecurityBuilder.securityContext(sharedConfigureSecurityContext ->
                sharedConfigureSecurityContext.securityContextRepository(new NullSecurityContextRepository()));
        sharedConfigureSecurityBuilder.requestCache(AbstractHttpConfigurer::disable);
        sharedConfigureSecurityBuilder.logout(AbstractHttpConfigurer::disable);
        sharedConfigureSecurityBuilder.addFilterBefore(new SharedSessionCheckFilter(
                sharedConfigureSecurityAppUserRepository, sharedConfigureSecurityLogWriter,
                sharedConfigureSecurityErrorResolver), CsrfFilter.class);

        // Role failures share the JSON shape and use a browser redirect for pages.
        sharedConfigureSecurityAnswers(sharedConfigureSecurityBuilder, sharedConfigureSecurityLogWriter,
                sharedConfigureSecurityCurrentUserReader, sharedConfigureSecurityErrorResolver);
        return sharedConfigureSecurityBuilder.build();
    }

    /**
     * What it does: Lists the paths the browser can open before signing in.
     * Input: The current HTTP request.
     * Output: True for a public endpoint or shared asset.
     */
    static boolean sharedCheckOpenWebAddress(HttpServletRequest sharedCheckOpenWebAddressRequest) {
        String sharedCheckOpenWebAddressPath = sharedCheckOpenWebAddressRequest.getServletPath();
        return sharedCheckOpenWebAddressPath.equals("/") || sharedCheckOpenWebAddressPath.equals("/index.html")
                || sharedCheckOpenWebAddressPath.startsWith("/shared/")
                || sharedCheckOpenWebAddressPath.equals("/api/shared/health")
                || sharedCheckOpenWebAddressPath.equals("/api/shared/login")
                || sharedCheckOpenWebAddressPath.equals("/api/shared/logout")
                || sharedCheckOpenWebAddressPath.equals("/api/shared/session");
    }

    // ===== SECTION 3: SECURITY TOKEN =====

    /**
     * What it does: Requires the session's security token in the request header.
     * Input: The security builder.
     * Output: The builder retains Spring Security's session token and masked token handling.
     */
    private void sharedConfigureSecurityToken(HttpSecurity sharedConfigureSecurityTokenBuilder) throws Exception {
        var sharedConfigureSecurityTokenHandler = new XorCsrfTokenRequestAttributeHandler();
        sharedConfigureSecurityTokenBuilder.csrf(sharedConfigureSecurityTokenConfiguration ->
                sharedConfigureSecurityTokenConfiguration.csrfTokenRequestHandler(new CsrfTokenRequestHandler() {

                    /**
                     * What it does: Makes the masked token available to the session controller.
                     * Input: The request, response and deferred session token.
                     * Output: The request carries the token for use by the controller.
                     */
                    @Override
                    public void handle(HttpServletRequest sharedConfigureSecurityTokenRequest,
                            HttpServletResponse sharedConfigureSecurityTokenResponse,
                            Supplier<CsrfToken> sharedConfigureSecurityTokenValue) {
                        sharedConfigureSecurityTokenHandler.handle(sharedConfigureSecurityTokenRequest,
                                sharedConfigureSecurityTokenResponse, sharedConfigureSecurityTokenValue);
                    }

                    /**
                     * What it does: Reads and unmasks a token sent through the required header.
                     * Input: The request and expected token.
                     * Output: The decoded token, or null if the header is missing or invalid.
                     */
                    @Override
                    public String resolveCsrfTokenValue(HttpServletRequest sharedConfigureSecurityTokenRequest,
                            CsrfToken sharedConfigureSecurityTokenValue) {
                        if (sharedConfigureSecurityTokenRequest.getHeader(
                                sharedConfigureSecurityTokenValue.getHeaderName()) == null) {
                            return null;
                        }
                        return sharedConfigureSecurityTokenHandler.resolveCsrfTokenValue(
                                sharedConfigureSecurityTokenRequest, sharedConfigureSecurityTokenValue);
                    }
                }));
    }

    // ===== SECTION 4: SESSION SETTINGS =====

    // Cookie-only tracking, HttpOnly, SameSite and the timeout are set in application.properties.
    // F1.1 creates a fresh servlet session on successful login.

    /**
     * What it does: Keeps Spring Boot from creating a separate default sign-in account.
     * Input: A username if a Spring authentication provider tries to load it.
     * Output: Spring reports that it does not manage the account. F1.1 owns username lookup.
     */
    @Bean
    UserDetailsService sharedFindLoginUser() {
        return sharedFindLoginUserUsername -> {
            throw new UsernameNotFoundException("Use the shared login service.");
        };
    }

    // ===== SECTION 5: ANSWERS WHEN NOT LOGGED IN OR NOT ALLOWED =====

    /**
     * What it does: Exposes caller metadata to the log writer before security filters can refuse a request.
     * Input: None.
     * Output: The listener that binds and clears the current HTTP request.
     */
    @Bean
    RequestContextListener sharedBindRequestContext() {
        return new RequestContextListener();
    }

    /**
     * What it does: Sets the fallback unauthenticated answer and the role-denial answer.
     * Input: The security builder, security log writer, current user reader and shared web error resolver.
     * Output: The builder redirects page requests and returns shared JSON for API requests.
     */
    private void sharedConfigureSecurityAnswers(HttpSecurity sharedConfigureSecurityAnswersBuilder,
            SharedSecurityLogWriter sharedConfigureSecurityAnswersLogWriter,
            SharedCurrentUserReader sharedConfigureSecurityAnswersCurrentUserReader,
            HandlerExceptionResolver sharedConfigureSecurityAnswersErrorResolver) throws Exception {
        sharedConfigureSecurityAnswersBuilder.exceptionHandling(sharedConfigureSecurityAnswersConfiguration ->
                sharedConfigureSecurityAnswersConfiguration
                        .authenticationEntryPoint((sharedConfigureSecurityAnswersRequest,
                                sharedConfigureSecurityAnswersResponse, sharedConfigureSecurityAnswersFailure) -> {
                            if (sharedConfigureSecurityAnswersRequest.getServletPath().startsWith("/api/")) {
                                sharedConfigureSecurityAnswersErrorResolver.resolveException(
                                        sharedConfigureSecurityAnswersRequest, sharedConfigureSecurityAnswersResponse,
                                        null, new SharedRuleException(HttpStatus.UNAUTHORIZED,
                                                "SHARED_NOT_LOGGED_IN", "Please log in."));
                                return;
                            }
                            sharedConfigureSecurityAnswersResponse.sendRedirect("/shared/shared-login.html");
                        })
                        .accessDeniedHandler((sharedConfigureSecurityAnswersRequest,
                                sharedConfigureSecurityAnswersResponse, sharedConfigureSecurityAnswersFailure) -> {
                            // Token failures can occur before a request has any signed-in user.
                            if (sharedConfigureSecurityAnswersFailure instanceof CsrfException) {
                                sharedConfigureSecurityAnswersErrorResolver.resolveException(
                                        sharedConfigureSecurityAnswersRequest, sharedConfigureSecurityAnswersResponse,
                                        null, new SharedRuleException(HttpStatus.FORBIDDEN,
                                                "SHARED_SECURITY_TOKEN_INVALID",
                                                "Your security token is missing or invalid."
                                                        + " Reload the page and try again."));
                                return;
                            }
                            String sharedConfigureSecurityAnswersPath =
                                    sharedConfigureSecurityAnswersRequest.getServletPath();
                            Long userID = sharedConfigureSecurityAnswersCurrentUserReader
                                    .sharedReadCurrentUser().userID();
                            sharedConfigureSecurityAnswersLogWriter.sharedWriteSecurityLog(userID,
                                    SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCESS_DENIED, "REJECTED",
                                    sharedConfigureSecurityAnswersPath);
                            if (sharedConfigureSecurityAnswersPath.startsWith("/api/")) {
                                sharedConfigureSecurityAnswersErrorResolver.resolveException(
                                        sharedConfigureSecurityAnswersRequest, sharedConfigureSecurityAnswersResponse,
                                        null, new SharedRuleException(HttpStatus.FORBIDDEN,
                                                "SHARED_ACCESS_DENIED", "You are not allowed to do this."));
                                return;
                            }
                            sharedConfigureSecurityAnswersResponse.sendRedirect(
                                    "/shared/shared-welcome.html?sharedShowWelcomeNotice=ACCESS_DENIED");
                        }));
    }
}
