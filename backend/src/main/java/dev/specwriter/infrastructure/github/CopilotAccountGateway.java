package dev.specwriter.infrastructure.github;

import dev.specwriter.domain.port.GitHubAuthenticator.ModelOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Opérations de compte du runtime Copilot (spike S1, research.md R4), isolées pour les tests. */
public interface CopilotAccountGateway {

    /** Connexion en cours : page d'autorisation à ouvrir et fin du flux. */
    record LoginFlow(String authorizationUrl, CompletableFuture<LoginResult> completion, Runnable cancel) {
    }

    /** Fin du flux : login connecté, ou refus, ou erreur. */
    record LoginResult(Outcome outcome, String login) {
        public enum Outcome {
            COMPLETED,
            DECLINED,
            ERROR
        }
    }

    LoginFlow beginLogin();

    /** Login connecté dans le COPILOT_HOME de l'outil (type {@code user}), à l'exclusion de la CLI gh. */
    Optional<String> connectedUser();

    List<ModelOption> models();

    void logoutAll();
}
