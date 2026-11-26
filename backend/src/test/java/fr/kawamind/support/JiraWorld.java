package fr.kawamind.support;

import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.application.specification.ProposalStore;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.jira.ConflictDetector;
import fr.kawamind.domain.jira.JiraIssue;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.DraftSpecification.DraftScenario;
import fr.kawamind.domain.llm.DraftSpecification.DraftSource;
import fr.kawamind.domain.llm.DraftSpecification.DraftUseCase;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.port.TextDiff;
import fr.kawamind.domain.port.WikiMarkup;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.settings.JiraSettings;
import fr.kawamind.domain.settings.SecretPersistence;
import fr.kawamind.domain.specification.MarkdownRenderer;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
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
    public final FakeDocumentFolder folder = new FakeDocumentFolder();
    public final InMemoryDocumentFileStates fileStates = new InMemoryDocumentFileStates();
    public final InMemorySpecifications versions = new InMemorySpecifications();
    public final InMemoryLinkedTickets tickets = new InMemoryLinkedTickets();
    public final InMemoryTicketLinks ticketLinks = new InMemoryTicketLinks();
    /** 024 : label du projet, labels des tickets, liens Jira perdus. */
    public final InMemoryJiraLabels jiraLabels = new InMemoryJiraLabels();
    public final InMemoryTicketLabels ticketLabels = new InMemoryTicketLabels();
    public final InMemoryLostJiraLinks lostLinks = new InMemoryLostJiraLinks();
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

    /** Jira configuré sans clé par défaut (012) : seuls les projets qui ont leur clé ont un projet Jira. */
    public JiraWorld configureJiraWithoutDefaultKey() {
        settings.saveJira(JiraSettings.create("https://jira.example.com", null, null, false, SecretPersistence.KEYCHAIN));
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
