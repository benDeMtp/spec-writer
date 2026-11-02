package dev.specwriter.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Connexion du compte GitHub Copilot (FR-030a à FR-030c, research.md R4 : voie A, autorisation
 * OAuth dans le navigateur portée par le runtime Copilot).
 */
public interface GitHubAuthenticator {

    ConnectionStart startConnection();

    ConnectionStatus poll(String flowId);

    void disconnect();

    /** Compte actuellement connecté, s'il y en a un. */
    Optional<String> connectedLogin();

    List<ModelOption> listModels();

    record ConnectionStart(String flowId, String authorizationUrl, List<String> requestedScopes, Instant expiresAt) {
        public ConnectionStart {
            requestedScopes = List.copyOf(requestedScopes);
        }
    }

    record ConnectionStatus(State state, String login) {
    }

    enum State {
        PENDING,
        CONNECTED,
        EXPIRED,
        DENIED,
        NO_COPILOT,
        ERROR
    }

    record ModelOption(String id, String name, boolean enabled) {
    }
}
