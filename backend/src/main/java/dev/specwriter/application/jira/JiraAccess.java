package dev.specwriter.application.jira;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.settings.JiraSettings;
import java.util.Optional;
import java.util.UUID;

/**
 * Accès au Jira configuré : réglages, jeton du trousseau et consentement {@code JIRA} (FR-024). Le
 * projet Jira est résolu en un point unique : la clé du projet de la session, sinon le projet par
 * défaut des réglages (FR-006 de 002).
 */
public class JiraAccess {

    /** Jira prêt à être appelé : adresse et jeton, projet Jira. */
    public record Target(JiraEndpoint endpoint, String projectKey) {

        Target inProject(String key) {
            return new Target(endpoint, key);
        }

        public String issueUrl(String issueKey) {
            String base = endpoint.serverUrl();
            return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/browse/" + issueKey;
        }
    }

    private final SettingsRepository settings;
    private final SecretStore secrets;
    private final ConsentPolicy consent;
    private final SessionRepository sessions;
    private final ProjectRepository projects;

    public JiraAccess(SettingsRepository settings, SecretStore secrets, ConsentPolicy consent, SessionRepository sessions,
            ProjectRepository projects) {
        this.settings = settings;
        this.secrets = secrets;
        this.consent = consent;
        this.sessions = sessions;
        this.projects = projects;
    }

    public boolean configured() {
        return settings.jira().isPresent();
    }

    public boolean consented() {
        return consent.isGranted(ConsentService.JIRA);
    }

    /** Projet Jira des actions sur cette session : la clé de son projet, sinon le projet par défaut. */
    public String effectiveProject(Session session) {
        return effectiveProject(projects.find(session.projectId()));
    }

    /** {@link #require()}, dans le projet Jira de la session ; {@code NOT_FOUND} si elle n'existe pas. */
    public Target require(UUID sessionId) {
        Target target = require();
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return target.inProject(effectiveProject(session));
    }

    public Target require(Session session) {
        return require().inProject(effectiveProject(session));
    }

    /** {@link #target()}, dans le projet Jira du projet courant (recherche, FR-005). */
    public Optional<Target> targetForCurrentProject() {
        return target().map(t -> t.inProject(effectiveProject(Optional.of(CurrentProject.resolve(projects)))));
    }

    /** Projet Jira d'un projet : sa clé, sinon le projet par défaut ; {@code null} si Jira n'est pas configuré. */
    public String effectiveProject(Project project) {
        return effectiveProject(Optional.of(project));
    }

    private String effectiveProject(Optional<Project> project) {
        String defaultKey = settings.jira().map(JiraSettings::defaultProjectKey).orElse(null);
        return project.flatMap(p -> p.effectiveJiraProject(defaultKey)).orElse(defaultKey);
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
