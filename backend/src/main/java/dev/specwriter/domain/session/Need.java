package dev.specwriter.domain.session;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Besoin initial en texte libre : 10 mots au minimum, 10 000 caractères au maximum (FR-001). Il
 * n'est plus modifiable une fois le questionnaire commencé : il n'a pas de modificateur.
 */
public record Need(String text) {

    public static final int MIN_WORDS = 10;
    public static final int MAX_LENGTH = 10_000;
    private static final int NAME_WORDS = 8;

    public Need {
        if (text == null || words(text).length < MIN_WORDS) {
            throw new DomainException(ErrorCode.NEED_TOO_SHORT);
        }
        if (text.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.NEED_TOO_LONG);
        }
    }

    public static Need of(String raw) {
        return new Need(raw == null ? null : raw.strip());
    }

    /** Nom de session par défaut : les premiers mots du besoin (data-model.md, Session.name). */
    public String defaultName() {
        String[] words = words(text);
        String start = Arrays.stream(words).limit(NAME_WORDS).collect(Collectors.joining(" "));
        if (start.length() > Session.MAX_NAME - 1) {
            start = start.substring(0, Session.MAX_NAME - 1);
        }
        return words.length > NAME_WORDS || start.length() < text.length() ? start + "…" : start;
    }

    private static String[] words(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? new String[0] : stripped.split("\\s+");
    }
}
