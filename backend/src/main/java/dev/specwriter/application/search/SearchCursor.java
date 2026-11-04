package dev.specwriter.application.search;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Curseur opaque de la liste unique (research.md R9) : décalages dans chaque source, éléments déjà
 * montrés et empreinte de la recherche, en JSON encodé en base64url et signé par HMAC avec une clé
 * tirée au démarrage. Un curseur modifié, ou venu d'une autre recherche, est refusé.
 */
final class SearchCursor {

    record State(int localOffset, int jiraOffset, int shown) {

        static final State START = new State(0, 0, 0);
    }

    private static final Pattern JSON = Pattern.compile(
            "\\{\"l\":(\\d{1,9}),\"j\":(\\d{1,9}),\"s\":(\\d{1,9}),\"q\":\"([0-9a-f]{64})\"}");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    SearchCursor() {
        this(random());
    }

    SearchCursor(byte[] key) {
        this.key = key.clone();
    }

    String encode(State state, String query) {
        String json = "{\"l\":" + state.localOffset() + ",\"j\":" + state.jiraOffset() + ",\"s\":" + state.shown()
                + ",\"q\":\"" + hash(query) + "\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
    }

    /** État du curseur ; le début de la liste s'il est absent. */
    State decode(String cursor, String query) {
        if (cursor == null || cursor.isBlank()) {
            return State.START;
        }
        String[] parts = cursor.split("\\.", -1);
        if (parts.length != 2) {
            throw invalid();
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = DECODER.decode(parts[0]);
            signature = DECODER.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(sign(payload), signature)) {
            throw invalid();
        }
        Matcher m = JSON.matcher(new String(payload, StandardCharsets.UTF_8));
        if (!m.matches() || !m.group(4).equals(hash(query))) {
            throw invalid();
        }
        return new State(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponible", e);
        }
    }

    private static String hash(String query) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(query.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static byte[] random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static DomainException invalid() {
        return new DomainException(ErrorCode.VALIDATION_FAILED,
                "Cette page de résultats n'est plus valable : relancez la recherche.");
    }
}
