package dev.specwriter.application.settings;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.JiraTestResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test de connexion Jira en 4 étapes, chacune avec sa cause d'échec (FR-032, research.md R10).
 * Aucun appel sans consentement JIRA (FR-024).
 */
public class TestJiraConnection {

    private static final Set<String> REQUIRED =
            Set.of(IssueTracker.PERM_BROWSE, IssueTracker.PERM_CREATE, IssueTracker.PERM_EDIT);

    public record Outcome(JiraTestResult result, String serverVersion, String userMessage) {
        static Outcome of(JiraTestResult r, String version) {
            return new Outcome(r, version, r.userMessage());
        }
    }

    private final IssueTracker tracker;
    private final SettingsRepository settings;
    private final SecretStore secrets;
    private final ConsentPolicy consent;
    private final ClockPort clock;

    public TestJiraConnection(IssueTracker tracker, SettingsRepository settings, SecretStore secrets,
            ConsentPolicy consent, ClockPort clock) {
        this.tracker = tracker;
        this.settings = settings;
        this.secrets = secrets;
        this.consent = consent;
        this.clock = clock;
    }

    public Outcome run() {
        JiraSettings jira = settings.jira().orElseThrow(() ->
                new dev.specwriter.domain.error.DomainException(dev.specwriter.domain.error.ErrorCode.JIRA_NOT_CONFIGURED));
        if (!consent.isGranted(ConsentService.JIRA)) {
            return Outcome.of(JiraTestResult.CONSENT_REQUIRED, null);
        }
        Optional<String> token = secrets.get(SecretStore.JIRA_PAT);
        Outcome outcome = token.map(t -> check(new JiraEndpoint(jira.serverUrl(), t, jira.caCertificatePath()),
                        jira.defaultProjectKey()))
                .orElseGet(() -> Outcome.of(JiraTestResult.TOKEN_REJECTED, null));
        settings.saveJira(jira.withTest(clock.now(), outcome.result(), outcome.serverVersion()));
        return outcome;
    }

    private Outcome check(JiraEndpoint endpoint, String projectKey) {
        String version = null;
        try {
            version = tracker.serverVersion(endpoint);
            if (!JiraSettings.isSupportedVersion(version)) {
                return Outcome.of(JiraTestResult.UNSUPPORTED_VERSION, version);
            }
            tracker.currentUser(endpoint);
            tracker.project(endpoint, projectKey);
            Map<String, Boolean> granted = tracker.permissions(endpoint, projectKey, REQUIRED);
            boolean all = REQUIRED.stream().allMatch(p -> Boolean.TRUE.equals(granted.get(p)));
            return Outcome.of(all ? JiraTestResult.OK : JiraTestResult.MISSING_PERMISSION, version);
        } catch (IssueTrackerException e) {
            JiraTestResult r = switch (e.kind()) {
                case UNREACHABLE, RATE_LIMITED, REJECTED -> JiraTestResult.UNREACHABLE;
                case UNAUTHORIZED -> JiraTestResult.TOKEN_REJECTED;
                case NOT_FOUND -> JiraTestResult.PROJECT_NOT_FOUND;
                case FORBIDDEN -> JiraTestResult.MISSING_PERMISSION;
            };
            return Outcome.of(r, version);
        }
    }
}
