package dev.specwriter.infrastructure.config;

import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.application.context.ProposeProjectContext;
import dev.specwriter.application.context.PreviewContextSources;
import dev.specwriter.application.context.ManageDocSources;
import dev.specwriter.application.context.EditContext;
import dev.specwriter.application.context.ContextProposalStore;
import dev.specwriter.application.context.AcceptContextProposal;
import dev.specwriter.application.consent.UpdateConsent;
import dev.specwriter.application.jira.CreateJiraTicket;
import dev.specwriter.application.jira.GetJiraIssue;
import dev.specwriter.application.jira.ImportJiraIssue;
import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.jira.PreviewJiraTicket;
import dev.specwriter.application.jira.PushToJira;
import dev.specwriter.application.project.CheckJiraProjectKey;
import dev.specwriter.application.project.CreateProject;
import dev.specwriter.application.project.DeleteProject;
import dev.specwriter.application.project.MoveSession;
import dev.specwriter.application.project.SelectCurrentProject;
import dev.specwriter.application.project.UpdateProject;
import dev.specwriter.application.search.UnifiedSearch;
import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.application.specification.AcceptAiEdit;
import dev.specwriter.application.specification.GenerateSpecification;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.application.specification.ProposeAiEdit;
import dev.specwriter.application.settings.DeleteJiraSettings;
import dev.specwriter.application.settings.DisconnectCopilot;
import dev.specwriter.application.settings.PollCopilotConnection;
import dev.specwriter.application.settings.SaveJiraSettings;
import dev.specwriter.application.settings.SelectModel;
import dev.specwriter.application.settings.StartCopilotConnection;
import dev.specwriter.application.settings.TestJiraConnection;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConsentRepository;
import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.port.TextDiff;
import dev.specwriter.domain.port.WikiMarkup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Câblage des cas d'usage, qui restent sans dépendance au framework (plan.md, architecture hexagonale). */
@ApplicationScoped
public class UseCaseProducer {

    @Produces
    @ApplicationScoped
    ConsentPolicy consentPolicy(ConsentRepository consents) {
        return new ConsentPolicy(consents);
    }

    @Produces
    @ApplicationScoped
    UpdateConsent updateConsent(ConsentRepository consents, ClockPort clock) {
        return new UpdateConsent(consents, clock);
    }

    @Produces
    @ApplicationScoped
    TestJiraConnection testJiraConnection(IssueTracker tracker, SettingsRepository settings, SecretStore secrets,
            ConsentPolicy consent, ClockPort clock) {
        return new TestJiraConnection(tracker, settings, secrets, consent, clock);
    }

    @Produces
    @ApplicationScoped
    SaveJiraSettings saveJiraSettings(SettingsRepository settings, SecretStore secrets) {
        return new SaveJiraSettings(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    DeleteJiraSettings deleteJiraSettings(SettingsRepository settings, SecretStore secrets) {
        return new DeleteJiraSettings(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    StartCopilotConnection startCopilotConnection(GitHubAuthenticator authenticator) {
        return new StartCopilotConnection(authenticator);
    }

    @Produces
    @ApplicationScoped
    PollCopilotConnection pollCopilotConnection(GitHubAuthenticator authenticator, SettingsRepository settings) {
        return new PollCopilotConnection(authenticator, settings);
    }

    @Produces
    @ApplicationScoped
    DisconnectCopilot disconnectCopilot(GitHubAuthenticator authenticator, SettingsRepository settings,
            SecretStore secrets) {
        return new DisconnectCopilot(authenticator, settings, secrets);
    }

    @Produces
    @ApplicationScoped
    SelectModel selectModel(SettingsRepository settings) {
        return new SelectModel(settings);
    }

    /** Version de l'outil, prise en compte dans l'empreinte des entrées (FR-014). */
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String toolVersion;

    @Produces
    @ApplicationScoped
    StartSession startSession(SessionRepository sessions, ProjectRepository projects, LlmClient llm,
            ConsentPolicy consent, ClockPort clock, IdGenerator ids) {
        return new StartSession(sessions, projects, llm, consent, clock, ids);
    }

    @Produces
    @ApplicationScoped
    AnswerQuestion answerQuestion(SessionRepository sessions, ProjectRepository projects, LlmClient llm,
            ConsentPolicy consent, ClockPort clock, IdGenerator ids) {
        return new AnswerQuestion(sessions, projects, llm, consent, clock, ids);
    }

    @Produces
    @ApplicationScoped
    ConcludeQuestionnaire concludeQuestionnaire(SessionRepository sessions, ClockPort clock) {
        return new ConcludeQuestionnaire(sessions, clock);
    }

    @Produces
    @ApplicationScoped
    GenerateSpecification generateSpecification(SessionRepository sessions, ProjectRepository projects,
            SpecificationRepository versions, LlmClient llm, ConsentPolicy consent, ClockPort clock, IdGenerator ids) {
        return new GenerateSpecification(sessions, projects, versions, llm, consent, clock, ids, toolVersion);
    }

    @Produces
    @ApplicationScoped
    ManageSessions manageSessions(SessionRepository sessions, SpecificationRepository versions,
            ProjectRepository projects, ClockPort clock) {
        return new ManageSessions(sessions, versions, projects, clock);
    }

    // ---------- Projets (feature 002, User Story 1) ----------

    @Produces
    @ApplicationScoped
    OperationRegistry operationRegistry() {
        return new OperationRegistry();
    }

    @Produces
    @ApplicationScoped
    CreateProject createProject(ProjectRepository projects, ClockPort clock, IdGenerator ids) {
        return new CreateProject(projects, clock, ids);
    }

    @Produces
    @ApplicationScoped
    UpdateProject updateProject(ProjectRepository projects, ClockPort clock) {
        return new UpdateProject(projects, clock);
    }

    @Produces
    @ApplicationScoped
    DeleteProject deleteProject(ProjectRepository projects, SessionRepository sessions, ManageSessions manageSessions,
            OperationRegistry operations, ClockPort clock) {
        return new DeleteProject(projects, sessions, manageSessions, operations, clock);
    }

    @Produces
    @ApplicationScoped
    SelectCurrentProject selectCurrentProject(ProjectRepository projects) {
        return new SelectCurrentProject(projects);
    }

    @Produces
    @ApplicationScoped
    CheckJiraProjectKey checkJiraProjectKey(JiraAccess jira, IssueTracker tracker) {
        return new CheckJiraProjectKey(jira, tracker);
    }

    @Produces
    @ApplicationScoped
    MoveSession moveSession(SessionRepository sessions, ProjectRepository projects, ClockPort clock) {
        return new MoveSession(sessions, projects, clock);
    }

    // ---------- Recherche et Jira (User Story 6) ----------

    @Produces
    @ApplicationScoped
    JiraAccess jiraAccess(SettingsRepository settings, SecretStore secrets, ConsentPolicy consent,
            SessionRepository sessions, ProjectRepository projects) {
        return new JiraAccess(settings, secrets, consent, sessions, projects);
    }

    @Produces
    @ApplicationScoped
    UnifiedSearch unifiedSearch(LocalSearch local, SpecificationRepository versions, LinkedTicketRepository tickets,
            IssueTracker tracker, JiraAccess jira, ProjectRepository projects,
            @ConfigProperty(name = "specwriter.jira.search-timeout", defaultValue = "5s") Duration timeout) {
        return new UnifiedSearch(local, versions, tickets, tracker, jira, projects, timeout);
    }

    @Produces
    @ApplicationScoped
    ProposalStore proposalStore(TextDiff diff, IdGenerator ids, ClockPort clock) {
        return new ProposalStore(diff, ids, clock);
    }

    @Produces
    @ApplicationScoped
    ProposeAiEdit proposeAiEdit(SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LlmClient llm, ConsentPolicy consent, ProposalStore proposals) {
        return new ProposeAiEdit(sessions, projects, versions, llm, consent, proposals);
    }

    @Produces
    @ApplicationScoped
    AcceptAiEdit acceptAiEdit(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids) {
        return new AcceptAiEdit(sessions, versions, tickets, proposals, clock, ids);
    }

    @Produces
    @ApplicationScoped
    GetJiraIssue getJiraIssue(IssueTracker tracker, JiraAccess jira, WikiMarkup markup) {
        return new GetJiraIssue(tracker, jira, markup);
    }

    @Produces
    @ApplicationScoped
    ImportJiraIssue importJiraIssue(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm,
            ConsentPolicy consent, SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, IdGenerator ids) {
        return new ImportJiraIssue(tracker, jira, markup, llm, consent, sessions, projects, versions, tickets, clock,
                ids);
    }

    @Produces
    @ApplicationScoped
    PushToJira pushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LinkedTicketRepository tickets, ProposalStore proposals, ClockPort clock, IdGenerator ids) {
        return new PushToJira(tracker, jira, markup, llm, consent, sessions, projects, versions, tickets, proposals, clock,
                ids);
    }

    @Produces
    @ApplicationScoped
    PreviewJiraTicket previewJiraTicket(SpecificationRepository versions, LinkedTicketRepository tickets, WikiMarkup markup,
            IssueTracker tracker, JiraAccess jira) {
        return new PreviewJiraTicket(versions, tickets, markup, tracker, jira);
    }

    @Produces
    @ApplicationScoped
    CreateJiraTicket createJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup,
            SpecificationRepository versions, LinkedTicketRepository tickets, ClockPort clock) {
        return new CreateJiraTicket(tracker, jira, markup, versions, tickets, clock);
    }

    // ---------- Contexte de projet (US2 de 002) ----------

    @Produces
    @ApplicationScoped
    ContextProposalStore contextProposalStore(TextDiff diff, IdGenerator ids, ClockPort clock) {
        return new ContextProposalStore(diff, ids, clock);
    }

    @Produces
    @ApplicationScoped
    ManageDocSources manageDocSources(ProjectRepository projects, DocSourceRepository docSources, IdGenerator ids) {
        return new ManageDocSources(projects, docSources, ids);
    }

    @Produces
    @ApplicationScoped
    PreviewContextSources previewContextSources(ProjectRepository projects, DocSourceRepository docSources,
            DocumentationReader reader, JiraAccess jira, IssueTracker tracker) {
        return new PreviewContextSources(projects, docSources, reader, jira, tracker);
    }

    @Produces
    @ApplicationScoped
    ProposeProjectContext proposeProjectContext(ProjectRepository projects, DocSourceRepository docSources,
            DocumentationReader reader, JiraAccess jira, IssueTracker tracker, WikiMarkup markup, LlmClient llm,
            ConsentPolicy consent, ContextProposalStore proposals, ClockPort clock) {
        return new ProposeProjectContext(projects, docSources, reader, jira, tracker, markup, llm, consent, proposals, clock);
    }

    @Produces
    @ApplicationScoped
    AcceptContextProposal acceptContextProposal(ProjectRepository projects, ContextProposalStore proposals, ClockPort clock) {
        return new AcceptContextProposal(projects, proposals, clock);
    }

    @Produces
    @ApplicationScoped
    EditContext editContext(ProjectRepository projects, ClockPort clock) {
        return new EditContext(projects, clock);
    }
}
