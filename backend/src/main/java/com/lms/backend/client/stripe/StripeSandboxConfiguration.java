package com.lms.backend.client.stripe;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.stripe.StripeClient;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "lms.client.stripe.enabled", havingValue = "true")
class StripeSandboxConfiguration {

    static final String WEBHOOK = "/api/webhooks/stripe";

    @Bean
    StripeSandboxGateway stripeSandboxGateway(
            ObjectMapper json, StripeSandboxRepository repository,
            @Value("${LMS_STRIPE_TEST_API_KEY:}") String key,
            @Value("${LMS_STRIPE_WEBHOOK_SECRET:}") String signingSecret,
            @Value("${LMS_STRIPE_RETURN_URL:}") String returnUrl,
            @Value("${LMS_STRIPE_CHECKOUT_SCOPE:}") String scope) {
        if (!key.matches("(?:sk|rk)_test_[A-Za-z0-9_]+") || !signingSecret.matches("whsec_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Stripe sandbox requires a test API key and webhook signing secret.");
        }
        URI target;
        try {
            target = URI.create(returnUrl);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Stripe sandbox requires a valid return URL.");
        }
        boolean https = "https".equalsIgnoreCase(target.getScheme());
        boolean local = "http".equalsIgnoreCase(target.getScheme())
                && ("localhost".equalsIgnoreCase(target.getHost()) || "127.0.0.1".equals(target.getHost()));
        if ((!https && !local) || target.getHost() == null || target.getRawUserInfo() != null) {
            throw new IllegalStateException("Stripe sandbox return URL must use HTTPS or loopback HTTP.");
        }
        if (!scope.matches("[A-Za-z0-9_-]{8,48}")) {
            throw new IllegalStateException("Stripe sandbox requires a stable, database-specific checkout scope.");
        }
        repository.requireSchema();
        var client = StripeClient.builder().setApiKey(key).setConnectTimeout(3000).setReadTimeout(5000)
                .setMaxNetworkRetries(0).build();
        return new StripeSandboxGateway(client, json, signingSecret, returnUrl, scope);
    }

    @Bean
    @Order(1)
    SecurityFilterChain stripeWebhookSecurity(HttpSecurity security) throws Exception {
        var webhook = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, WEBHOOK);
        return security.securityMatcher(webhook).authorizeHttpRequests(rules -> rules.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers(webhook)).build();
    }
}
