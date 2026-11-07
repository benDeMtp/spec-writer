package dev.specwriter.application.settings;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.GitHubAuthenticator.ModelOption;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.CopilotSettings.SubscriptionState;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Relit les modèles autorisés et leur capacité d'un compte déjà connecté (008) : une connexion
 * faite avant la 008 n'a aucune capacité, et une limite peut changer. Le modèle choisi est gardé
 * s'il reste autorisé. Sans réponse de Copilot, les réglages ne changent pas.
 */
public class RefreshModelCapacities {

    private static final Logger LOG = Logger.getLogger(RefreshModelCapacities.class);

    private final GitHubAuthenticator authenticator;
    private final SettingsRepository settings;

    public RefreshModelCapacities(GitHubAuthenticator authenticator, SettingsRepository settings) {
        this.authenticator = authenticator;
        this.settings = settings;
    }

    /** Vrai si les capacités ont été relues. */
    public boolean run() {
        CopilotSettings current = settings.copilot();
        if (current.connectedLogin() == null || current.subscriptionState() != SubscriptionState.ACTIVE) {
            return false;
        }
        List<ModelOption> enabled;
        try {
            enabled = authenticator.listModels().stream().filter(ModelOption::enabled).toList();
        } catch (DomainException e) {
            LOG.warnf("Capacités des modèles non relues (%s)", e.code());
            return false;
        }
        if (enabled.isEmpty()) {
            return false;
        }
        // relues pendant que l'utilisateur a pu changer de modèle : on repart des réglages du moment
        settings.saveCopilot(settings.copilot().connected(current.connectedLogin(),
                enabled.stream().map(ModelOption::id).toList(), enabled.stream().map(ModelOption::capacity).toList()));
        return true;
    }
}
