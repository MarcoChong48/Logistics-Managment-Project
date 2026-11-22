package com.lms.backend.client.account;

import java.io.InputStream;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.error.SharedRuleException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@RestController
@RequestMapping("/api/client/account")
class ClientAccountController {

    private final ClientAccountService accounts;
    private final ObjectReader defaultsReader;

    ClientAccountController(ClientAccountService accounts, ObjectMapper json) {
        this.accounts = accounts;
        this.defaultsReader = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @GetMapping
    ResponseEntity<ClientAccountResponse> account() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.account());
    }

    @GetMapping("/delivery-defaults")
    ResponseEntity<ClientDeliveryDefaults> deliveryDefaults() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.deliveryDefaults());
    }

    @PutMapping(path = "/delivery-defaults", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
    ResponseEntity<ClientDeliveryDefaults> replaceDeliveryDefaults(InputStream request) {
        JsonNode body;
        try {
            body = defaultsReader.readValue(request);
        } catch (JacksonException exception) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID", "The request could not be read.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.replaceDeliveryDefaults(ClientDeliveryDefaults.fromRequest(body)));
    }
}
