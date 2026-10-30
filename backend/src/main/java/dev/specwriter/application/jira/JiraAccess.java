package dev.specwriter.application.jira;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.JiraSettings;
import java.util.Optional;

/** Accès au Jira configuré : réglages, jeton du trousseau et consentement {@code JIRA} (FR-024). */
public class JiraAccess {

    /** Jira prêt à être appelé : adresse et jeton, projet par défaut. */
    public record Target(JiraEndpoint endpoint, String projectKey) {

        public String issueUrl(String issueKey) {
            String base = endpoint.serverUrl();
            return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/browse/" + issueKey;
        }
    }

    private final SettingsRepository settings;
    private final SecretStore secrets;
    private final ConsentPolicy consent;

    public JiraAccess(SettingsRepository settings, SecretStore secrets, ConsentPolicy consent) {
        this.settings = settings;
        this.secrets = secrets;
        this.consent = consent;
    }

    public boolean configured() {
        return settings.jira().isPresent();
    }

    public boolean consented() {
        return consent.isGranted(ConsentService.JIRA);
    }

    /** Vide si Jira n'est pas configuré ou si le jeton manque ; ne vérifie pas le consentement. */
    public Optional<Target> target() {
        Optional<JiraSettings> jira = settings.jira();
        if (jira.isEmpty()) {
            return Optional.empty();
        }
        JiraSettings j = jira.get();
        return secrets.get(SecretStore.JIRA_PAT)
                .map(token -> new Target(new JiraEndpoint(j.serverUrl(), token, j.caCertificatePath()), j.defaultProjectKey()));
    }

    /** Consentement d'abord, puis configuration : {@code CONSENT_REQUIRED} ou {@code JIRA_NOT_CONFIGURED}. */
    public Target require() {
        consent.requireGranted(ConsentService.JIRA);
        if (!configured()) {
            throw new DomainException(ErrorCode.JIRA_NOT_CONFIGURED);
        }
        return target().orElseThrow(() -> new DomainException(ErrorCode.JIRA_TOKEN_REJECTED));
    }
}
