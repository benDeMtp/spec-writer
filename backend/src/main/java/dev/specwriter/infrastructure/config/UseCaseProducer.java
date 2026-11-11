package dev.specwriter.infrastructure.config;

import dev.specwriter.application.agent.AgentChangeStatus;
import dev.specwriter.application.agent.AgentJournal;
import dev.specwriter.application.agent.FeatureKnowledge;
import dev.specwriter.application.agent.GetFeature;
import dev.specwriter.application.agent.ListFeatures;
import dev.specwriter.application.agent.ListProjectsForAgent;
import dev.specwriter.application.agent.ManageAgentAccess;
import dev.specwriter.application.agent.SearchKnowledgeForAgent;
import dev.specwriter.application.attachment.AttachmentDrafts;
import dev.specwriter.application.attachment.ManageAttachments;
import dev.specwriter.application.consent.UpdateConsent;
import dev.specwriter.application.context.EditContext;
import dev.specwriter.application.context.ManageDocSources;
import dev.specwriter.application.sources.ManageSourceRoots;
import dev.specwriter.application.conversation.ConversationSearch;
import dev.specwriter.application.conversation.ConversationAttachments;
import dev.specwriter.application.conversation.ConversationSuggestions;
import dev.specwriter.application.conversation.Converse;
import dev.specwriter.application.conversation.DiscussSpecification;
import dev.specwriter.application.conversation.ProposeRevision;
import dev.specwriter.application.conversation.DesignatedDocuments;
import dev.specwriter.application.conversation.DraftNeed;
import dev.specwriter.application.conversation.ManageConversations;
import dev.specwriter.application.conversation.StartFromConversation;
import dev.specwriter.application.conversation.TurnEvents;
import dev.specwriter.application.jira.CreateJiraTicket;
import dev.specwriter.application.jira.GetJiraIssue;
import dev.specwriter.application.jira.ImportJiraIssue;
import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.jira.ListProjectIssues;
import dev.specwriter.application.jira.PreviewJiraTicket;
import dev.specwriter.application.jira.PushToJira;
import dev.specwriter.application.jira.RetryRelatedLink;
import dev.specwriter.application.knowledge.AnalyzeArchitecture;
import dev.specwriter.application.knowledge.CompareWithIssue;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.application.knowledge.Indexations;
import dev.specwriter.application.knowledge.KnowledgeQueries;
import dev.specwriter.application.knowledge.ListAdrs;
import dev.specwriter.application.knowledge.ManageArchitectureSource;
import dev.specwriter.application.knowledge.ManageEmbeddingSettings;
import dev.specwriter.application.knowledge.MentionQueries;
import dev.specwriter.application.knowledge.ReadDocFile;
import dev.specwriter.application.knowledge.RelatedIssuesStore;
import dev.specwriter.application.knowledge.ReportSubtasks;
import dev.specwriter.application.knowledge.SelectKnowledge;
import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.application.knowledge.StoryFamily;
import dev.specwriter.application.knowledge.SynchronizeKnowledge;
import dev.specwriter.application.project.CheckJiraProjectKey;
import dev.specwriter.application.project.CreateProject;
import dev.specwriter.application.project.DeleteProject;
import dev.specwriter.application.project.MoveSession;
import dev.specwriter.application.project.SelectCurrentProject;
import dev.specwriter.application.project.UpdateProject;
import dev.specwriter.application.search.UnifiedSearch;
import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ChangeStatus;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.EvolveSpecification;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.ResolveRelatedIssue;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.application.settings.DeleteJiraSettings;
import dev.specwriter.application.settings.DisconnectCopilot;
import dev.specwriter.application.settings.PollCopilotConnection;
import dev.specwriter.application.settings.RefreshModelCapacities;
import dev.specwriter.application.settings.SaveJiraSettings;
import dev.specwriter.application.settings.SelectModel;
import dev.specwriter.application.settings.StartCopilotConnection;
import dev.specwriter.application.settings.TestJiraConnection;
import dev.specwriter.application.specification.AcceptAiEdit;
import dev.specwriter.application.specification.DeleteVersion;
import dev.specwriter.application.specification.GenerateSpecification;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.application.specification.ProposeAiEdit;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.port.AgentAccessRepository;
import dev.specwriter.domain.port.AgentEndpoint;
import dev.specwriter.domain.port.ArchitectureReader;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.AttachmentRepository;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConsentRepository;
import dev.specwriter.domain.port.ConversationAttachmentRepository;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentFileStates;
import dev.specwriter.domain.port.DocumentFiles;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SourceRootRepository;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SessionRelationRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.port.TextDiff;
import dev.specwriter.domain.port.TicketLinkRepository;
import dev.specwriter.domain.port.WikiMarkup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Câblage des cas d'usage, qui restent sans dépendance au framework (plan.md, architecture hexagonale). */
@ApplicationScoped
public class UseCaseProducer {

    /** Documents joints avant le démarrage, en mémoire (009, research.md R3). */
    @Produces
    @ApplicationScoped
    AttachmentDrafts attachmentDrafts(AttachmentReader reader, ClockPort clock, IdGenerator ids) {
        return new AttachmentDrafts(reader, clock, ids);
    }

    /** Événements des tours de l'agent, en mémoire (010, research.md R2). */
    @Produces
    @ApplicationScoped
    TurnEvents turnEvents(ClockPort clock) {
        return new TurnEvents(clock);
    }

    /** Agent conversationnel (010) : chaque tour tourne sur un fil virtuel. */
    @Produces
    @ApplicationScoped
    Converse converse(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            KnowledgeIndex index, EmbeddingPort embedding, LocalSearch specifications, ModelCapacities capacities,
            ConsentPolicy consent, OperationRegistry operations, TurnEvents events, ClockPort clock, IdGenerator ids,
            AnalyzeArchitecture analyze, DocSourceRepository docSources, DocumentFolder folder,
            ConversationAttachmentRepository attachments, DiscussSpecification discussions) {
        SelectKnowledge select = new SelectKnowledge(projects, index, embedding, analyze, StoryFamily.none(), docSources,
                folder, capacities);
        return new Converse(projects, conversations, llm, new ConversationSearch(index, embedding, specifications),
                new DesignatedDocuments(select, attachments), capacities, consent, operations, events, clock, ids,
                task -> Thread.ofVirtual().name("conversation").start(task), discussions);
    }

    /** « Proposer une nouvelle version » d'après la discussion (011, US2) : la rédaction tourne sur un fil virtuel. */
    @Produces
    @ApplicationScoped
    ProposeRevision proposeRevision(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, SessionKnowledge knowledge,
            ConversationLlm llm, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ProposalStore proposals, ClockPort clock, IdGenerator ids) {
        return new ProposeRevision(sessions, versions, conversations, discussions, knowledge, llm, capacities, consent,
                operations, events, proposals, clock, ids, task -> Thread.ofVirtual().name("proposition").start(task));
    }

    /** Discussions des spécifications (011). */
    @Produces
    @ApplicationScoped
    DiscussSpecification discussSpecification(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, SpecificationDiscussionRepository discussions, ClockPort clock,
            IdGenerator ids, AttachmentRepository attachments) {
        return new DiscussSpecification(sessions, versions, conversations, discussions, clock, ids, attachments);
    }

    @Produces
    @ApplicationScoped
    ManageConversations manageConversations(ProjectRepository projects, ConversationRepository conversations,
            SessionRepository sessions, JiraAccess jira, OperationRegistry operations,
            SpecificationDiscussionRepository discussions) {
        return new ManageConversations(projects, conversations, sessions, jira, operations, discussions);
    }

    @Produces
    @ApplicationScoped
    ConversationSuggestions conversationSuggestions(KnowledgeIndex index) {
        return new ConversationSuggestions(index);
    }

    @Produces
    @ApplicationScoped
    DraftNeed draftNeed(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ModelCapacities capacities, ConsentPolicy consent, ClockPort clock) {
        return new DraftNeed(projects, conversations, llm, capacities, consent, clock);
    }

    @Produces
    @ApplicationScoped
    StartFromConversation startFromConversation(ConversationRepository conversations, ProjectRepository projects,
            StartSession startSession, ConversationAttachments attachments) {
        return new StartFromConversation(conversations, projects, startSession, attachments::copies);
    }

    @Produces
    @ApplicationScoped
    ConversationAttachments conversationAttachments(ConversationRepository conversations,
            ConversationAttachmentRepository attachments, AttachmentReader reader, ClockPort clock, IdGenerator ids) {
        return new ConversationAttachments(conversations, attachments, reader, clock, ids);
    }

    /** Documents joints d'une session démarrée (009, US3). */
    @Produces
    @ApplicationScoped
    ManageAttachments manageAttachments(SessionRepository sessions, AttachmentRepository attachments,
            AttachmentReader reader, ClockPort clock, IdGenerator ids) {
        return new ManageAttachments(sessions, attachments, reader, clock, ids);
    }

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
            ConsentPolicy consent, ProjectRepository projects, ClockPort clock) {
        return new TestJiraConnection(tracker, settings, secrets, consent, projects, clock);
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
    RefreshModelCapacities refreshModelCapacities(GitHubAuthenticator authenticator, SettingsRepository settings) {
        return new RefreshModelCapacities(authenticator, settings);
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
            ConsentPolicy consent, ClockPort clock, IdGenerator ids, FindRelatedIssues findRelated,
            RelatedIssuesStore related, SessionKnowledge knowledge, AttachmentDrafts drafts,
            AttachmentRepository attachments) {
        return new StartSession(sessions, projects, llm, consent, clock, ids, findRelated, related, knowledge, drafts,
                attachments);
    }

    @Produces
    @ApplicationScoped
    AnalyzeArchitecture analyzeArchitecture(KnowledgeIndex index, LlmClient llm, ConsentPolicy consent) {
        return new AnalyzeArchitecture(index, llm, consent);
    }

    /** Connaissance transmise aux opérations de l'IA (research.md R24), selon le modèle choisi (008). */
    @Produces
    @ApplicationScoped
    SessionKnowledge sessionKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze, StoryFamily families, DocSourceRepository docSources, DocumentFolder folder,
            ModelCapacities capacities, AttachmentRepository attachments) {
        return new SelectKnowledge(projects, index, embedding, analyze, families, docSources, folder, capacities,
                attachments);
    }

    /** Mentions @ d'un document (007) : suggestions et état, avec la place calculée comme à la génération. */
    @Produces
    @ApplicationScoped
    MentionQueries mentionQueries(ProjectRepository projects, SessionRepository sessions, KnowledgeIndex index,
            EmbeddingPort embedding, AnalyzeArchitecture analyze, ModelCapacities capacities, AttachmentDrafts drafts,
            AttachmentRepository attachments, ConversationAttachmentRepository conversationAttachments) {
        return new MentionQueries(projects, sessions, index,
                new SelectKnowledge(projects, index, embedding, analyze, StoryFamily.none(), null, null, capacities,
                        attachments), drafts, attachments, conversationAttachments);
    }

    /** Sous-tâches de la story liée, rattachées aux cas d'usage d'une version (research.md R29). */
    @Produces
    @ApplicationScoped
    ReportSubtasks reportSubtasks(SessionRepository sessions, SpecificationRepository versions, StoryFamily families,
            KnowledgeIndex index, EmbeddingPort embedding, JiraAccess jira, IssueTracker tracker,
            @ConfigProperty(name = "specwriter.knowledge.subtask-min-z", defaultValue = "1.5") double minZ) {
        return new ReportSubtasks(sessions, versions, families, index, embedding, jira, tracker, minZ);
    }

    /** Famille de la story liée à une session : parent, sous-tâches et liens (research.md R29). */
    @Produces
    @ApplicationScoped
    StoryFamily storyFamily(LinkedTicketRepository tickets, TicketLinkRepository links) {
        return new StoryFamily(tickets, links);
    }

    @Produces
    @ApplicationScoped
    RelatedIssuesStore relatedIssuesStore() {
        return new RelatedIssuesStore();
    }

    @Produces
    @ApplicationScoped
    FindRelatedIssues findRelatedIssues(KnowledgeIndex index, EmbeddingPort embedding, LlmClient llm,
            ConsentPolicy consent, JiraAccess jira, IssueTracker tracker, Indexations indexations,
            KnowledgeSourceRepository sources, StoryFamily families) {
        return new FindRelatedIssues(index, embedding, llm, consent, jira, tracker, indexations, sources, families);
    }

    @Produces
    @ApplicationScoped
    CompareWithIssue compareWithIssue(SessionRepository sessions, RelatedIssuesStore related, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, LlmClient llm, ConsentPolicy consent) {
        return new CompareWithIssue(sessions, related, jira, tracker, markup, llm, consent);
    }

    @Produces
    @ApplicationScoped
    ResolveRelatedIssue resolveRelatedIssue(SessionRepository sessions, ProjectRepository projects,
            RelatedIssuesStore related, SessionRelationRepository relations, ImportJiraIssue importIssue,
            ProposeAiEdit proposeEdit, ManageSessions manageSessions, LlmClient llm, ClockPort clock, IdGenerator ids,
            SessionKnowledge knowledge) {
        return new ResolveRelatedIssue(sessions, projects, related, relations, importIssue, proposeEdit, manageSessions,
                llm, clock, ids, knowledge);
    }

    @Produces
    @ApplicationScoped
    RetryRelatedLink retryRelatedLink(IssueTracker tracker, JiraAccess jira, LinkedTicketRepository tickets,
            SessionRelationRepository relations) {
        return new RetryRelatedLink(tracker, jira, tickets, relations);
    }

    @Produces
    @ApplicationScoped
    AnswerQuestion answerQuestion(SessionRepository sessions, LlmClient llm, ConsentPolicy consent, ClockPort clock,
            IdGenerator ids, SessionKnowledge knowledge) {
        return new AnswerQuestion(sessions, llm, consent, clock, ids, knowledge);
    }

    @Produces
    @ApplicationScoped
    ConcludeQuestionnaire concludeQuestionnaire(SessionRepository sessions, ClockPort clock) {
        return new ConcludeQuestionnaire(sessions, clock);
    }

    @Produces
    @ApplicationScoped
    GenerateSpecification generateSpecification(SessionRepository sessions, SpecificationRepository versions,
            LlmClient llm, ConsentPolicy consent, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge,
            OperationRegistry operations) {
        return new GenerateSpecification(sessions, versions, llm, consent, clock, ids, toolVersion, knowledge, operations);
    }

    @Produces
    @ApplicationScoped
    ManageSessions manageSessions(SessionRepository sessions, SpecificationRepository versions,
            ProjectRepository projects, ClockPort clock, ProposalStore proposals, OperationRegistry operations,
            ConversationRepository conversations, SpecificationDiscussionRepository discussions) {
        return new ManageSessions(sessions, versions, projects, clock, proposals, operations, conversations,
                discussions);
    }

    @Produces
    @ApplicationScoped
    DeleteVersion deleteVersion(SessionRepository sessions, SpecificationRepository versions,
            LinkedTicketRepository tickets, ProposalStore proposals, OperationRegistry operations, ClockPort clock) {
        return new DeleteVersion(sessions, versions, tickets, proposals, operations, clock);
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
    ProposeAiEdit proposeAiEdit(SessionRepository sessions, SpecificationRepository versions, LlmClient llm,
            ConsentPolicy consent, ProposalStore proposals, SessionKnowledge knowledge, OperationRegistry operations) {
        return new ProposeAiEdit(sessions, versions, llm, consent, proposals, knowledge, operations);
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
    AgentJournal agentJournal(AgentAccessRepository access, ClockPort clock, IdGenerator ids) {
        return new AgentJournal(access, clock, ids);
    }

    @Produces
    @ApplicationScoped
    ManageAgentAccess manageAgentAccess(AgentAccessRepository access, AgentEndpoint endpoint, ClockPort clock) {
        return new ManageAgentAccess(access, endpoint, clock);
    }

    @Produces
    @ApplicationScoped
    ListProjectsForAgent listProjectsForAgent(ProjectRepository projects, AgentJournal journal) {
        return new ListProjectsForAgent(projects, journal);
    }

    @Produces
    @ApplicationScoped
    ListFeatures listFeatures(ProjectRepository projects, SessionRepository sessions, SpecificationRepository versions,
            AgentJournal journal) {
        return new ListFeatures(projects, sessions, versions, journal);
    }

    @Produces
    @ApplicationScoped
    GetFeature getFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProjectRepository projects, KnowledgeIndex index, AgentJournal journal) {
        return new GetFeature(sessions, versions, tickets, new FeatureKnowledge(projects, index), journal);
    }

    @Produces
    @ApplicationScoped
    SearchKnowledgeForAgent searchKnowledgeForAgent(KnowledgeQueries queries) {
        return new SearchKnowledgeForAgent(queries);
    }

    @Produces
    @ApplicationScoped
    AgentChangeStatus agentChangeStatus(SessionRepository sessions, ClockPort clock, AgentJournal journal) {
        return new AgentChangeStatus(sessions, clock, journal);
    }

    @Produces
    @ApplicationScoped
    ChangeStatus changeStatus(SessionRepository sessions, ClockPort clock) {
        return new ChangeStatus(sessions, clock);
    }

    @Produces
    @ApplicationScoped
    EvolveSpecification evolveSpecification(SessionRepository sessions, SpecificationRepository versions,
            ProjectRepository projects, StartSession start) {
        return new EvolveSpecification(sessions, versions, projects, start);
    }

    @Produces
    @ApplicationScoped
    ListProjectIssues listProjectIssues(IssueTracker tracker, JiraAccess jira, LinkedTicketRepository tickets) {
        return new ListProjectIssues(tracker, jira, tickets);
    }

    @Produces
    @ApplicationScoped
    ImportJiraIssue importJiraIssue(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm,
            ConsentPolicy consent, SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge) {
        return new ImportJiraIssue(tracker, jira, markup, llm, consent, sessions, projects, versions, tickets, clock,
                ids, knowledge);
    }

    @Produces
    @ApplicationScoped
    PushToJira pushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge,
            OperationRegistry operations) {
        return new PushToJira(tracker, jira, markup, llm, consent, sessions, versions, tickets, proposals, clock, ids,
                knowledge, operations);
    }

    @Produces
    @ApplicationScoped
    PreviewJiraTicket previewJiraTicket(SpecificationRepository versions, LinkedTicketRepository tickets, WikiMarkup markup,
            IssueTracker tracker, JiraAccess jira, SessionRelationRepository relations) {
        return new PreviewJiraTicket(versions, tickets, markup, tracker, jira, relations);
    }

    @Produces
    @ApplicationScoped
    CreateJiraTicket createJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup,
            SpecificationRepository versions, LinkedTicketRepository tickets, ClockPort clock,
            SessionRelationRepository relations) {
        return new CreateJiraTicket(tracker, jira, markup, versions, tickets, clock, relations);
    }

    // ---------- Fiche générale et connaissance du projet (US2 de 002) ----------

    @Produces
    @ApplicationScoped
    ManageDocSources manageDocSources(ProjectRepository projects, DocSourceRepository docSources, IdGenerator ids,
            KnowledgeIndex index, DocumentFolder folder) {
        return new ManageDocSources(projects, docSources, ids, index, folder);
    }

    @Produces
    @ApplicationScoped
    ManageSourceRoots manageSourceRoots(ProjectRepository projects, SourceRootRepository roots, SourceTree tree,
            IdGenerator ids, ClockPort clock) {
        return new ManageSourceRoots(projects, roots, tree, ids, clock);
    }

    @Produces
    @ApplicationScoped
    SynchronizeKnowledge synchronizeKnowledge(ProjectRepository projects, KnowledgeSourceRepository sources,
            DocSourceRepository docSources, DocumentationReader reader, DocumentFolder folder,
            DocumentFileStates fileStates, ArchitectureReader architecture, JiraAccess jira, IssueTracker tracker,
            TicketLinkRepository links, KnowledgeIndex index, EmbeddingPort embedding, ClockPort clock) {
        return new SynchronizeKnowledge(projects, sources, docSources, reader, folder, fileStates, architecture, jira,
                tracker, links, index, embedding, clock);
    }

    @Produces
    @ApplicationScoped
    Indexations indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, ClockPort clock,
            @ConfigProperty(name = "specwriter.knowledge.auto-index", defaultValue = "true") boolean automatic) {
        return new Indexations(synchronize, projects, task -> Thread.ofVirtual().name("indexation").start(task), clock,
                automatic);
    }

    @Produces
    @ApplicationScoped
    ManageEmbeddingSettings manageEmbeddingSettings(SettingsRepository settings, EmbeddingPort embedding) {
        return new ManageEmbeddingSettings(settings, embedding);
    }

    @Produces
    @ApplicationScoped
    ListAdrs listAdrs(ProjectRepository projects, KnowledgeIndex index, DocSourceRepository docSources,
            DocumentFiles files, Indexations indexations) {
        return new ListAdrs(projects, index, docSources, files, indexations);
    }

    @Produces
    @ApplicationScoped
    ReadDocFile readDocFile(ProjectRepository projects, DocSourceRepository docSources, DocumentFiles files) {
        return new ReadDocFile(projects, docSources, files);
    }

    @Produces
    @ApplicationScoped
    KnowledgeQueries knowledgeQueries(ProjectRepository projects, KnowledgeIndex index, KnowledgeSourceRepository sources,
            EmbeddingPort embedding, Indexations indexations, JiraAccess jira) {
        return new KnowledgeQueries(projects, index, sources, embedding, indexations, jira);
    }

    @Produces
    @ApplicationScoped
    ManageArchitectureSource manageArchitectureSource(ProjectRepository projects, KnowledgeSourceRepository sources,
            KnowledgeIndex index, Indexations indexations) {
        return new ManageArchitectureSource(projects, sources, index, indexations);
    }

    @Produces
    @ApplicationScoped
    EditContext editContext(ProjectRepository projects, ClockPort clock) {
        return new EditContext(projects, clock);
    }
}
