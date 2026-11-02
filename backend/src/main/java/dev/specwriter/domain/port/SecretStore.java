package dev.specwriter.domain.port;

import java.util.Optional;

/**
 * Accès aux secrets (research.md R8). Les secrets ne sont jamais écrits dans un fichier de
 * l'application (FR-033a) : soit dans le trousseau du système, soit en mémoire (FR-033b).
 */
public interface SecretStore {

    String JIRA_PAT = "jira.pat";
    String COPILOT_ACCESS = "copilot.access";
    String COPILOT_REFRESH = "copilot.refresh";

    void put(String key, String value);

    Optional<String> get(String key);

    void delete(String key);

    Persistence persistence();

    enum Persistence {
        KEYCHAIN,
        MEMORY_ONLY
    }
}
