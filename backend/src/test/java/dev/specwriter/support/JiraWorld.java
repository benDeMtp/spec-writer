package dev.specwriter.support;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftSource;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.TextDiff;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.SecretPersistence;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import java.time.Instant;
import java.util.List;

/** Dépôts en mémoire, Jira et LLM simulés : le décor commun des tests de la user story 6. */
public class JiraWorld {

    public static final String NEED = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.";
    public static final Instant JIRA_T0 = Instant.parse("2026-10-30T08:00:00Z");

    public final InMemorySessions sessions = new InMemorySessions();
    public final InMemoryProjects projects = new InMemoryProjects();
    public final InMemoryDocSources docSources = new InMemoryDocSources();
    public final FakeDocumentationReader docs = new FakeDocumentationReader();
    public final InMemorySpecifications versions = new InMemorySpecifications();
    public final InMemoryLinkedTickets tickets = new InMemoryLinkedTickets();
    public final InMemoryConsents consents = new InMemoryConsents();
    public final InMemorySettings settings = new InMemorySettings();
    public final InMemorySecrets secrets = new InMemorySecrets();
    public final FakeIssueTracker tracker = new FakeIssueTracker();
    public final FakeLocalSearch local = new FakeLocalSearch();
    public final StubLlm llm = new StubLlm();
    public final FixedClock clock = new FixedClock();
    public final SequentialIdGenerator ids = new SequentialIdGenerator();
    public final ConsentPolicy policy = new ConsentPolicy(consents);
    public final JiraAccess jira = new JiraAccess(settings, secrets, policy, sessions, projects);
    /** Conversion réversible et visible : {@code wiki:} devant le Markdown. */
    public final WikiMarkup markup = new WikiMarkup() {
        @Override
        public String toWiki(String markdown) {
            return "wiki:" + markdown;
        }

        @Override
        public String toMarkdown(String wiki, String issueUrl) {
            return wiki == null ? "" : wiki.startsWith("wiki:") ? wiki.substring(5) : wiki;
        }

        @Override
        public String wikiTablesToMarkdown(String markdown) {
            return markdown == null ? "" : markdown;
        }
    };
    public final TextDiff diff = (before, after, l1, l2) -> "--- " + l1 + "\n+++ " + l2 + "\n";
    public final ProposalStore proposals = new ProposalStore(diff, ids, clock);

    public JiraWorld grant(ConsentService service) {
        consents.record(new ConsentRecord(service, true, Instant.EPOCH));
        return this;
    }

    public JiraWorld configureJira() {
        settings.saveJira(JiraSettings.create("https://jira.example.com", "PROJ", null, false, SecretPersistence.KEYCHAIN));
        secrets.put(SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        return this;
    }

    /** Session locale générée, avec sa version 1. */
    public Session generatedSession() {
        Session s = Session.start(ids.next(), Project.DEFAULT_ID, Need.of(NEED), null, clock.now())
                .generated(clock.now());
        sessions.save(s, Questionnaire.empty());
        versions.save(new SpecificationVersion(ids.next(), s.id(), 1, Specs.valid(), "fp", VersionOrigin.GENERATED,
                MarkdownRenderer.render(Specs.valid(), s.language()), clock.now()));
        return s;
    }

    /** Lie la session à un ticket dont Jira a la description donnée, lue à {@link #JIRA_T0}. */
    public LinkedJiraTicket link(Session s, String key, String wikiDescription) {
        tracker.issues.put(key, new JiraIssue(key, "Réserver une salle", wikiDescription, JIRA_T0));
        LinkedJiraTicket t = new LinkedJiraTicket(s.id(), key, "https://jira.example.com/browse/" + key, "Story", 1, JIRA_T0,
                ConflictDetector.hash(wikiDescription), JIRA_T0, null, LinkedJiraTicket.SyncState.IN_SYNC);
        tickets.save(t);
        return t;
    }

    /** Modification faite dans Jira, après la dernière lecture. */
    public void editInJira(String key, String wikiDescription) {
        JiraIssue old = tracker.issues.get(key);
        tracker.issues.put(key, new JiraIssue(key, old.summary(), wikiDescription, old.updated().plusSeconds(600)));
    }

    public static DraftSpecification draft(String title) {
        return new DraftSpecification(title, "Réserver une salle",
                List.of(new DraftUseCase("Réserver", "employé", "réserver une salle", List.of(), List.of("choisir"), List.of(),
                        new DraftSource("NEED_EXCERPT", "", "réserver une salle"))),
                List.of(new DraftScenario(0, "Réservation", "NOMINAL", "employé", List.of(), List.of("je réserve"),
                        List.of("la salle est réservée"), false)),
                List.of(), List.of());
    }

    /** Sortie qui échoue au contrôle de structure : scénario sans « Quand ». */
    public static DraftSpecification invalidDraft() {
        return new DraftSpecification("T", "R",
                List.of(new DraftUseCase("Réserver", "employé", "réserver", List.of(), List.of("choisir"), List.of(),
                        new DraftSource("NEED_EXCERPT", "", "réserver"))),
                List.of(new DraftScenario(0, "Sans action", "NOMINAL", "employé", List.of(), List.of(), List.of("ok"), false)),
                List.of(), List.of());
    }
}
