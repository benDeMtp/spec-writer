package dev.specwriter.application.settings;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.settings.TestJiraConnection.Outcome;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.JiraTestResult;
import dev.specwriter.domain.settings.SecretPersistence;
import dev.specwriter.support.FakeIssueTracker;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemorySecrets;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestJiraConnectionTest {

    static class FakeTracker extends FakeIssueTracker {
        final List<String> calls = new ArrayList<>();
        String version = "9.12.3";
        Kind failOn;
        String failStep;
        Map<String, Boolean> perms = Map.of(PERM_BROWSE, true, PERM_CREATE, true, PERM_EDIT, true);

        private void step(String name) {
            calls.add(name);
            if (name.equals(failStep)) {
                throw new IssueTrackerException(failOn, name);
            }
        }

        @Override
        public String serverVersion(JiraEndpoint e) {
            step("serverInfo");
            return version;
        }

        @Override
        public String currentUser(JiraEndpoint e) {
            step("myself");
            return "ada";
        }

        final List<String> keys = new ArrayList<>();

        @Override
        public void project(JiraEndpoint e, String key) {
            keys.add(key);
            step("project");
        }

        @Override
        public Map<String, Boolean> permissions(JiraEndpoint e, String key, Set<String> p) {
            step("mypermissions");
            return perms;
        }
    }

    private final FakeTracker tracker = new FakeTracker();
    private final InMemorySettings settings = new InMemorySettings();
    private final InMemorySecrets secrets = new InMemorySecrets();
    private final InMemoryConsents consents = new InMemoryConsents();
    private final FixedClock clock = new FixedClock();
    private final InMemoryProjects projects = new InMemoryProjects();
    private TestJiraConnection useCase;

    @BeforeEach
    void setUp() {
        settings.saveJira(JiraSettings.create("https://jira.example.com", "PROJ", null, false, SecretPersistence.KEYCHAIN));
        secrets.put(dev.specwriter.domain.port.SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        consents.record(new ConsentRecord(ConsentService.JIRA, true, clock.now()));
        useCase = new TestJiraConnection(tracker, settings, secrets, new ConsentPolicy(consents), projects, clock);
    }

    @Test
    void quatreEtapesDansLOrdreEtSucces() {
        Outcome o = useCase.run();
        assertThat(tracker.calls).containsExactly("serverInfo", "myself", "project", "mypermissions");
        assertThat(o.result()).isEqualTo(JiraTestResult.OK);
        assertThat(o.serverVersion()).isEqualTo("9.12.3");
        // projet courant sans clé : la clé par défaut est vérifiée
        assertThat(o.projectKey()).isEqualTo("PROJ");
        assertThat(tracker.keys).containsExactly("PROJ");
        assertThat(settings.jira().orElseThrow().lastTest().result()).isEqualTo(JiraTestResult.OK);
        assertThat(settings.jira().orElseThrow().serverVersion()).isEqualTo("9.12.3");
    }

    @Test
    void cleDuProjetCourant() {
        // 012 (FR-017) : le test porte sur la clé effective du projet courant
        Project facturation = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null,
                JiraProjectKey.ofNullable("FACT"), Instant.EPOCH);
        projects.save(facturation);
        projects.saveCurrentProjectId(facturation.id());
        Outcome o = useCase.run();
        assertThat(o.result()).isEqualTo(JiraTestResult.OK);
        assertThat(o.projectKey()).isEqualTo("FACT");
        assertThat(tracker.keys).containsExactly("FACT");
    }

    @Test
    void sansCleNiCleParDefautServeurEtJetonSeulement() {
        settings.saveJira(JiraSettings.create("https://jira.example.com", null, null, false, SecretPersistence.KEYCHAIN));
        Outcome o = useCase.run();
        assertThat(o.result()).isEqualTo(JiraTestResult.OK_NO_PROJECT);
        assertThat(o.projectKey()).isNull();
        assertThat(o.userMessage()).contains("réglages du projet");
        assertThat(tracker.calls).containsExactly("serverInfo", "myself");
    }

    @Test
    void injoignable() {
        tracker.failStep = "serverInfo";
        tracker.failOn = Kind.UNREACHABLE;
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.UNREACHABLE);
        assertThat(tracker.calls).containsExactly("serverInfo");
    }

    @Test
    void versionTropAncienne() {
        tracker.version = "8.13.0";
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.UNSUPPORTED_VERSION);
        assertThat(tracker.calls).containsExactly("serverInfo");
    }

    @Test
    void jetonRefuse() {
        tracker.failStep = "myself";
        tracker.failOn = Kind.UNAUTHORIZED;
        Outcome o = useCase.run();
        assertThat(o.result()).isEqualTo(JiraTestResult.TOKEN_REJECTED);
        assertThat(o.userMessage()).contains("jeton");
    }

    @Test
    void projetIntrouvable() {
        tracker.failStep = "project";
        tracker.failOn = Kind.NOT_FOUND;
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.PROJECT_NOT_FOUND);
    }

    @Test
    void droitManquant() {
        tracker.perms = Map.of(IssueTracker.PERM_BROWSE, true, IssueTracker.PERM_CREATE, false, IssueTracker.PERM_EDIT, true);
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.MISSING_PERMISSION);
        tracker.perms = Map.of(IssueTracker.PERM_BROWSE, true, IssueTracker.PERM_CREATE, true);
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.MISSING_PERMISSION);
    }

    @Test
    void sansJetonCommeJetonRefuse() {
        secrets.delete(dev.specwriter.domain.port.SecretStore.JIRA_PAT);
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.TOKEN_REJECTED);
        assertThat(tracker.calls).isEmpty();
    }

    @Test
    void sansConsentementAucunAppel() {
        consents.record(new ConsentRecord(ConsentService.JIRA, false, clock.now()));
        assertThat(useCase.run().result()).isEqualTo(JiraTestResult.CONSENT_REQUIRED);
        assertThat(tracker.calls).isEmpty();
    }

    @Test
    void chaqueResultatAUnMessage() {
        for (JiraTestResult r : JiraTestResult.values()) {
            assertThat(r.userMessage()).isNotBlank();
        }
    }
}
