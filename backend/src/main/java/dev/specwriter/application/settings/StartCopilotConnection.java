package dev.specwriter.application.settings;

import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.GitHubAuthenticator.ConnectionStart;

/** Démarre l'autorisation GitHub dans le navigateur (FR-030a). */
public class StartCopilotConnection {

    private final GitHubAuthenticator authenticator;

    public StartCopilotConnection(GitHubAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public ConnectionStart run() {
        return authenticator.startConnection();
    }
}
