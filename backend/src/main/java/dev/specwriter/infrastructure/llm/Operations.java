package dev.specwriter.infrastructure.llm;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chaque message système commence par un marqueur {@code [operation:<nom>]} (contracts/llm-port.md).
 * Il sert au rejeu déterministe en test et en E2E, jamais à interpréter le contenu utilisateur.
 */
public final class Operations {

    private static final Pattern MARKER = Pattern.compile("^\\s*\\[operation:([a-z-]+)]");

    private Operations() {
    }

    public static Optional<String> of(String systemPrompt) {
        if (systemPrompt == null) {
            return Optional.empty();
        }
        Matcher m = MARKER.matcher(systemPrompt);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
