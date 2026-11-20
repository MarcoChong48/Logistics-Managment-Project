package com.lms.backend.client.order;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;

final class ClientOrderFingerprint {

    private ClientOrderFingerprint() {
    }

    static byte[] compute(SortedMap<Long, Integer> quantities, String recipient, String phone, String address) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }

        // Version 1 uses fixed-width item numbers, followed by length-prefixed UTF-8 text fields.
        digest.update((byte) 1);
        updateSize(digest, quantities.size());
        ByteBuffer item = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
        quantities.forEach((productId, quantity) -> {
            item.clear().putLong(productId).putInt(quantity);
            digest.update(item.array());
        });
        updateText(digest, recipient);
        updateText(digest, phone);
        updateText(digest, address);
        return digest.digest();
    }

    private static void updateText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateSize(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateSize(MessageDigest digest, int size) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(size).array());
    }
}
