package dev.specwriter.domain.specification;

import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.session.Language;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Empreinte SHA-256 des entrées d'une génération : besoin, réponses, version de l'outil, langue et
 * contexte du projet (FR-014, FR-018, research.md R12 ; FR-011 de 002). Indépendante de l'ordre de
 * stockage des réponses. Sans contexte, elle est celle de 001 : les versions existantes restent
 * réutilisables.
 */
public final class InputFingerprint {

    private InputFingerprint() {
    }

    public static String of(String need, List<Question> treated, String toolVersion, Language language) {
        return of(need, treated, toolVersion, language, "");
    }

    public static String of(String need, List<Question> treated, String toolVersion, Language language,
            String contextHash) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        field(digest, "need", need);
        field(digest, "tool", toolVersion);
        field(digest, "language", language.code());
        if (contextHash != null && !contextHash.isEmpty()) {
            field(digest, "context", contextHash);
        }
        treated.stream()
                .filter(q -> q.answer() != null)
                .sorted(Comparator.comparing(q -> q.id().toString()))
                .forEach(q -> {
                    field(digest, "question", q.id().toString());
                    field(digest, "answer", q.answer().value());
                    field(digest, "assumption", Boolean.toString(q.answer().assumption()));
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Longueur préfixée : deux découpages différents ne donnent jamais la même suite d'octets. */
    private static void field(MessageDigest digest, String name, String value) {
        byte[] bytes = (name + "=" + (value == null ? "" : value)).getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }
}
