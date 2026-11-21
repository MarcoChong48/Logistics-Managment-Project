package com.lms.backend.client.account;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import com.lms.backend.shared.error.SharedRuleException;

import tools.jackson.databind.JsonNode;

record ClientDeliveryDefaults(String recipientName, String contactPhone, String deliveryAddress) {

    static final ClientDeliveryDefaults EMPTY = new ClientDeliveryDefaults(null, null, null);
    private static final Pattern PHONE_FORMAT = Pattern.compile("\\+?[0-9]+(?:[ -][0-9]+)*");

    static ClientDeliveryDefaults fromRequest(JsonNode request) {
        if (!request.isObject() || request.size() != 3 || !request.has("recipientName")
                || !request.has("contactPhone") || !request.has("deliveryAddress")) {
            throw invalid("Supply recipientName, contactPhone and deliveryAddress. Use null to clear a field.");
        }
        String name = text(request.get("recipientName"), "Recipient name", 120, false);
        if (name != null && name.codePoints().noneMatch(Character::isLetter)) {
            throw invalid("Recipient name must include a letter.");
        }
        String phone = text(request.get("contactPhone"), "Contact phone", 40, false);
        if (phone != null) {
            long digits = phone.chars().filter(Character::isDigit).count();
            if (digits < 7 || digits > 15 || !PHONE_FORMAT.matcher(phone).matches()) {
                throw invalid("Contact phone must contain 7 to 15 digits and may use a leading +, spaces or hyphens.");
            }
        }
        String address = text(request.get("deliveryAddress"), "Hong Kong delivery address", 1000, true);
        if (address != null && address.codePoints().noneMatch(Character::isLetterOrDigit)) {
            throw invalid("Hong Kong delivery address must include a letter or digit.");
        }
        return new ClientDeliveryDefaults(name, phone, address);
    }

    private static String text(JsonNode field, String label, int maxLength, boolean multiline) {
        if (field.isNull()) {
            return null;
        }
        if (!field.isString()) {
            throw invalid(label + " must be a string or null.");
        }
        String raw = field.stringValue();
        if (multiline) {
            raw = raw.replace("\r\n", "\n");
        }
        if (raw.codePoints().anyMatch(codePoint -> invalidCharacter(codePoint, multiline))) {
            throw invalid(label + " contains invalid text.");
        }
        String value = raw.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maxLength) {
            throw invalid(label + " must be between 1 and " + maxLength + " characters, or null.");
        }
        return value;
    }

    private static boolean invalidCharacter(int codePoint, boolean multiline) {
        if (multiline && codePoint == '\n') {
            return false;
        }
        return Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029
                || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }

    private static SharedRuleException invalid(String message) {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "CLIENT_DELIVERY_DEFAULTS_INVALID", message);
    }
}
