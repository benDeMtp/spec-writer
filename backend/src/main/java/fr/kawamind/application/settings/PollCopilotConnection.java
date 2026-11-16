package fr.kawamind.application.settings;

import fr.kawamind.domain.port.GitHubAuthenticator;
import fr.kawamind.domain.port.GitHubAuthenticator.ConnectionStatus;
import fr.kawamind.domain.port.GitHubAuthenticator.ModelOption;
import fr.kawamind.domain.port.GitHubAuthenticator.State;
import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.settings.CopilotSettings;
import java.util.List;

/**
 * Relit l'état de la connexion en cours. Une fois connecté, les modèles autorisés pour le compte
 * sont enregistrés, avec leur capacité (008), et le modèle par défaut choisi (FR-030c) ; sans Copilot, NO_COPILOT (US1-3).
 */
public class PollCopilotConnection {

    private final GitHubAuthenticator authenticator;
    private final SettingsRepository settings;

    public PollCopilotConnection(GitHubAuthenticator authenticator, SettingsRepository settings) {
        this.authenticator = authenticator;
        this.settings = settings;
    }

    public ConnectionStatus run(String flowId) {
        ConnectionStatus status = authenticator.poll(flowId);
        if (status.state() == State.CONNECTED) {
            List<ModelOption> enabled = authenticator.listModels().stream().filter(ModelOption::enabled).toList();
            List<String> models = enabled.stream().map(ModelOption::id).toList();
            CopilotSettings updated = settings.copilot().connected(status.login(), models,
                    enabled.stream().map(ModelOption::capacity).toList());
            settings.saveCopilot(updated);
            if (models.isEmpty()) {
                return new ConnectionStatus(State.NO_COPILOT, status.login());
            }
        } else if (status.state() == State.NO_COPILOT) {
            settings.saveCopilot(settings.copilot().connected(status.login(), List.of()));
        }
        return status;
    }
}
