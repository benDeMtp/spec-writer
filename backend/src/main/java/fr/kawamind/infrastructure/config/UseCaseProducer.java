package fr.kawamind.infrastructure.config;

import fr.kawamind.application.agent.AgentChangeStatus;
import fr.kawamind.application.agent.AgentJournal;
import fr.kawamind.application.agent.FeatureKnowledge;
import fr.kawamind.application.agent.GetFeature;
import fr.kawamind.application.agent.ListFeatures;
import fr.kawamind.application.agent.ListProjectsForAgent;
import fr.kawamind.application.agent.ManageAgentAccess;
import fr.kawamind.application.agent.SearchKnowledgeForAgent;
import fr.kawamind.application.attachment.AttachmentDrafts;
import fr.kawamind.application.attachment.ManageAttachments;
import fr.kawamind.application.consent.UpdateConsent;
import fr.kawamind.application.context.EditContext;
import fr.kawamind.application.context.ManageDocSources;
import fr.kawamind.application.context.ManageReliability;
import fr.kawamind.application.sources.AnalyzePerimeter;
import fr.kawamind.application.sources.ManageSourceRoots;
import fr.kawamind.application.conversation.ConversationSearch;
import fr.kawamind.application.conversation.ConversationAttachments;
import fr.kawamind.application.conversation.ConversationSuggestions;
import fr.kawamind.application.conversation.Converse;
import fr.kawamind.application.conversation.DiscussSpecification;
import fr.kawamind.application.conversation.ProposeRevision;
import fr.kawamind.application.conversation.DesignatedDocuments;
import fr.kawamind.application.conversation.DraftNeed;
import fr.kawamind.application.conversation.ManageConversations;
import fr.kawamind.application.conversation.StartFromConversation;
import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.jira.CreateJiraTicket;
import fr.kawamind.application.jira.GetJiraIssue;
import fr.kawamind.application.jira.ImportJiraIssue;
import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.application.jira.ListProjectIssues;
import fr.kawamind.application.jira.PreviewJiraTicket;
import fr.kawamind.application.jira.PushToJira;
import fr.kawamind.application.jira.RetryRelatedLink;
import fr.kawamind.application.knowledge.AnalyzeArchitecture;
import fr.kawamind.application.knowledge.CompareWithIssue;
import fr.kawamind.application.knowledge.FindRelatedIssues;
import fr.kawamind.application.knowledge.AutoSync;
import fr.kawamind.application.knowledge.Indexations;
import fr.kawamind.application.knowledge.ManageSourceSync;
import fr.kawamind.application.knowledge.KnowledgeQueries;
import fr.kawamind.application.knowledge.ListAdrs;
import fr.kawamind.application.knowledge.ManageArchitectureSource;
import fr.kawamind.application.knowledge.ManageEmbeddingSettings;
import fr.kawamind.application.lifecycle.ExitPort;
import fr.kawamind.application.lifecycle.GracefulStop;
import fr.kawamind.application.lifecycle.LongWorkTracker;
import fr.kawamind.application.lifecycle.RunningWork;
import fr.kawamind.application.settings.ManageBrowser;
import fr.kawamind.application.settings.ManageNotifications;
import fr.kawamind.application.onboarding.ManageOnboarding;
import fr.kawamind.application.knowledge.MentionQueries;
import fr.kawamind.application.knowledge.ReadDocFile;
import fr.kawamind.application.knowledge.RelatedIssuesStore;
import fr.kawamind.application.knowledge.ReportSubtasks;
import fr.kawamind.application.knowledge.ProvenanceResolver;
import fr.kawamind.application.knowledge.SelectKnowledge;
import fr.kawamind.application.knowledge.SessionKnowledge;
import fr.kawamind.application.knowledge.StoryFamily;
import fr.kawamind.application.knowledge.SynchronizeKnowledge;
import fr.kawamind.application.project.CheckJiraProjectKey;
import fr.kawamind.application.project.CreateProject;
import fr.kawamind.application.project.DeleteProject;
import fr.kawamind.application.project.MoveSession;
import fr.kawamind.application.project.SelectCurrentProject;
import fr.kawamind.application.project.UpdateProject;
import fr.kawamind.application.search.UnifiedSearch;
import fr.kawamind.application.session.AnswerQuestion;
import fr.kawamind.application.session.ChangeStatus;
import fr.kawamind.application.session.ConcludeQuestionnaire;
import fr.kawamind.application.session.EvolveSpecification;
import fr.kawamind.application.session.ManageSessions;
import fr.kawamind.application.session.ResolveRelatedIssue;
import fr.kawamind.application.session.StartSession;
import fr.kawamind.application.knowledge.ConfluenceIndexation;
import fr.kawamind.application.settings.ConfluenceAccess;
import fr.kawamind.application.settings.DeleteConfluenceSettings;
import fr.kawamind.application.settings.DeleteJiraSettings;
import fr.kawamind.application.settings.SaveConfluenceSettings;
import fr.kawamind.application.settings.TestConfluenceConnection;
import fr.kawamind.application.settings.DisconnectCopilot;
import fr.kawamind.application.settings.PollCopilotConnection;
import fr.kawamind.application.settings.RefreshModelCapacities;
import fr.kawamind.application.settings.SaveJiraSettings;
import fr.kawamind.application.settings.SelectModel;
import fr.kawamind.application.settings.StartCopilotConnection;
import fr.kawamind.application.settings.TestJiraConnection;
import fr.kawamind.application.specification.AcceptAiEdit;
import fr.kawamind.application.specification.DeleteVersion;
import fr.kawamind.application.specification.GenerateSpecification;
import fr.kawamind.application.interview.InterviewRevision;
import fr.kawamind.application.interview.PrepareInterview;
import fr.kawamind.application.interview.SourceUpdates;
import fr.kawamind.domain.port.SourceUpdateRepository;
import fr.kawamind.application.interview.RunInterview;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.application.specification.ProposalStore;
import fr.kawamind.application.specification.ProposeAiEdit;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.port.AgentAccessRepository;
import fr.kawamind.domain.port.AgentEndpoint;
import fr.kawamind.domain.port.ArchitectureReader;
import fr.kawamind.domain.port.AttachmentReader;
import fr.kawamind.domain.port.AttachmentRepository;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConsentRepository;
import fr.kawamind.domain.port.ConversationAttachmentRepository;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluencePageStates;
import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.port.DocumentFileStates;
import fr.kawamind.domain.port.ReliabilityRepository;
import fr.kawamind.domain.port.DocumentFiles;
import fr.kawamind.domain.port.DocumentFolder;
import fr.kawamind.domain.port.DocumentationReader;
import fr.kawamind.domain.port.BrowserPort;
import fr.kawamind.domain.port.EmbeddingPort;
import fr.kawamind.domain.port.GitHubAuthenticator;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.KnowledgeSourceRepository;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.port.LocalSearch;
import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.port.PerimeterRepository;
import fr.kawamind.domain.port.AppEvents;
import fr.kawamind.domain.port.ListedIssues;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.port.SourceRootRepository;
import fr.kawamind.domain.port.SourceTree;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.port.SessionRelationRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.OnboardingRepository;
import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.port.SpecificationDiscussionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.port.TextDiff;
import fr.kawamind.domain.port.TicketLinkRepository;
import fr.kawamind.domain.port.WikiMarkup;
import fr.kawamind.application.task.AgentTasks;
import fr.kawamind.application.task.EditProposal;
import fr.kawamind.application.task.TaskJira;
import fr.kawamind.application.task.TaskSync;
import fr.kawamind.application.task.TrackingActions;
import fr.kawamind.application.task.EditTasks;
import fr.kawamind.application.task.ProposeTasks;
import fr.kawamind.application.task.ReadTasks;
import fr.kawamind.application.task.TaskDesk;
import fr.kawamind.application.task.TaskLocks;
import fr.kawamind.application.task.ValidateTasks;
import fr.kawamind.domain.port.AgentConnections;
import fr.kawamind.domain.port.TaskLlm;
import fr.kawamind.domain.port.TaskRepository;
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

    // Tâches d'une spécification (015)

    @Produces
    @ApplicationScoped
    TaskLocks taskLocks() {
        return new TaskLocks();
    }

    /** Singleton, sans proxy : les cas d'usage des tâches lisent ses champs. */
    @Produces
    @jakarta.inject.Singleton
    TaskDesk taskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks) {
        return new TaskDesk(sessions, tasks, tickets, jira, tracker, markup, clock, ids, locks);
    }

    @Produces
    @ApplicationScoped
    ReadTasks readTasks(TaskDesk desk, AgentConnections connections, TaskSync sync) {
        return new ReadTasks(desk, connections, sync);
    }

    @Produces
    @ApplicationScoped
    TaskJira taskJira(IssueTracker tracker) {
        return new TaskJira(tracker);
    }

    @Produces
    @ApplicationScoped
    TaskSync taskSync(TaskDesk desk, TaskJira jira) {
        return new TaskSync(desk, jira);
    }

    @Produces
    @ApplicationScoped
    TrackingActions trackingActions(TaskDesk desk, TaskSync sync, TaskJira jira, ReadTasks read, AgentJournal journal) {
        return new TrackingActions(desk, sync, jira, read, journal);
    }

    @Produces
    @ApplicationScoped
    AgentTasks agentTasks(TaskDesk desk, TaskSync sync, TaskJira jira, AgentConnections connections,
            AgentJournal journal) {
        return new AgentTasks(desk, sync, jira, connections, journal);
    }

    @Produces
    @ApplicationScoped
    ProposeTasks proposeTasks(TaskDesk desk, SpecificationRepository versions, PerimeterRepository perimeters, TaskLlm llm,
            ConsentPolicy consent) {
        return new ProposeTasks(desk, versions, perimeters, llm, consent);
    }

    @Produces
    @ApplicationScoped
    EditProposal editProposal(TaskDesk desk) {
        return new EditProposal(desk);
    }

    @Produces
    @ApplicationScoped
    ValidateTasks validateTasks(TaskDesk desk, ReadTasks read) {
        return new ValidateTasks(desk, read);
    }

    @Produces
    @ApplicationScoped
    EditTasks editTasks(TaskDesk desk, ReadTasks read) {
        return new EditTasks(desk, read);
    }

    /** Générations synchrones en cours, nommées avant Quitter (019, research.md R4). */
    @Produces
    @ApplicationScoped
    LongWorkTracker longWorkTracker() {
        return new LongWorkTracker();
    }

    /** Travaux en cours : indexations, tours, générations (019, research.md R4). */
    @Produces
    @ApplicationScoped
    RunningWork runningWork(Indexations indexations, TurnEvents turns, LongWorkTracker generations,
            ProjectRepository projects, ConversationRepository conversations) {
        return new RunningWork(indexations, turns, generations, projects, conversations);
    }

    /** Arrêt propre, commun à Quitter, ⌘Q, Ctrl+C et SIGTERM (019, research.md R4). */
    @Produces
    @ApplicationScoped
    GracefulStop gracefulStop(RunningWork work, ExitPort exit, ClockPort clock) {
        return new GracefulStop(work, exit, clock, d -> Thread.sleep(d));
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
            ConversationAttachmentRepository attachments, DiscussSpecification discussions, AnalyzePerimeter perimeter,
            PerimeterRepository perimeters, ProvenanceResolver provenance) {
        SelectKnowledge select = new SelectKnowledge(projects, index, embedding, analyze, StoryFamily.none(), docSources,
                folder, capacities, null, provenance);
        return new Converse(projects, conversations, llm,
                new ConversationSearch(index, embedding, specifications, provenance),
                new DesignatedDocuments(select, attachments), capacities, consent, operations, events, clock, ids,
                task -> Thread.ofVirtual().name("conversation").start(task), discussions, perimeter, perimeters);
    }

    /** Analyse de périmètre dans les sources du code (013). */
    @Produces
    @ApplicationScoped
    AnalyzePerimeter analyzePerimeter(SourceRootRepository roots, SourceTree tree, ConversationLlm llm, ClockPort clock,
            KnowledgeIndex index) {
        return new AnalyzePerimeter(roots, tree, llm, clock, SourceTree.Limits.DEFAULT, index);
    }

    /** « Proposer une nouvelle version » d'après la discussion (011, US2) : la rédaction tourne sur un fil virtuel. */
    @Produces
    @ApplicationScoped
    ProposeRevision proposeRevision(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, SessionKnowledge knowledge,
            ConversationLlm llm, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ProposalStore proposals, ClockPort clock, IdGenerator ids, PerimeterRepository perimeters,
            InterviewRepository interviews) {
        return new ProposeRevision(sessions, versions, conversations, discussions, knowledge, llm, capacities, consent,
                operations, events, proposals, clock, ids, task -> Thread.ofVirtual().name("proposition").start(task),
                perimeters, interviews);
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
            SpecificationDiscussionRepository discussions, PerimeterRepository perimeters) {
        return new ManageConversations(projects, conversations, sessions, jira, operations, discussions, perimeters);
    }

    @Produces
    @ApplicationScoped
    ConversationSuggestions conversationSuggestions(KnowledgeIndex index) {
        return new ConversationSuggestions(index);
    }

    @Produces
    @ApplicationScoped
    DraftNeed draftNeed(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ModelCapacities capacities, ConsentPolicy consent, ClockPort clock, PerimeterRepository perimeters) {
        return new DraftNeed(projects, conversations, llm, capacities, consent, clock, perimeters);
    }

    @Produces
    @ApplicationScoped
    StartFromConversation startFromConversation(ConversationRepository conversations, ProjectRepository projects,
            StartSession startSession, ConversationAttachments attachments, PerimeterRepository perimeters,
            ClockPort clock) {
        return new StartFromConversation(conversations, projects, startSession, attachments::copies, perimeters, clock);
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
            ModelCapacities capacities, AttachmentRepository attachments, ProvenanceResolver provenance) {
        return new SelectKnowledge(projects, index, embedding, analyze, families, docSources, folder, capacities,
                attachments, provenance);
    }

    /** Source, date et niveau de fiabilité des extraits transmis à l'IA (016, research.md R5). */
    @Produces
    @ApplicationScoped
    ProvenanceResolver provenanceResolver(DocSourceRepository docSources, ReliabilityRepository reliability,
            ConfluencePageStates pages) {
        return new ProvenanceResolver(docSources, reliability, pages);
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
            OperationRegistry operations, PerimeterRepository perimeters) {
        return new GenerateSpecification(sessions, versions, llm, consent, clock, ids, toolVersion, knowledge, operations,
                perimeters);
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

    // ---------- Interview d'une spécification (021) ----------

    @Produces
    @ApplicationScoped
    RunInterview runInterview(InterviewRepository interviews, SessionRepository sessions, SpecificationRepository versions,
            OperationRegistry operations, ClockPort clock, IdGenerator ids) {
        return new RunInterview(interviews, sessions, versions, operations, clock, ids);
    }

    /** « À reporter » vers les sources de vérité (021, US6). */
    @Produces
    @ApplicationScoped
    SourceUpdates sourceUpdates(SourceUpdateRepository updates, InterviewRepository interviews,
            SessionRepository sessions, SpecificationRepository versions, KnowledgeIndex knowledge, ClockPort clock,
            IdGenerator ids) {
        return new SourceUpdates(updates, interviews, sessions, versions, knowledge, clock, ids);
    }

    /** Préparation d'une séance de plusieurs spécifications (021, US5). */
    @Produces
    @ApplicationScoped
    PrepareInterview prepareInterview(InterviewRepository interviews, SessionRepository sessions,
            SpecificationRepository versions, OperationRegistry operations, RunInterview run, ClockPort clock,
            IdGenerator ids) {
        return new PrepareInterview(interviews, sessions, versions, operations, run, clock, ids);
    }

    /** Révision d'une spécification d'après la séance (021, US3) : la rédaction de la 011. */
    @Produces
    @ApplicationScoped
    InterviewRevision interviewRevision(InterviewRepository interviews, SessionRepository sessions,
            SpecificationRepository versions, ConversationRepository conversations, DiscussSpecification discussions,
            ProposeRevision propose, ConsentPolicy consent, ClockPort clock, IdGenerator ids) {
        return new InterviewRevision(interviews, sessions, versions, conversations, discussions, propose, consent, clock,
                ids);
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
    ProposalStore proposalStore(TextDiff diff, IdGenerator ids, ClockPort clock, PerimeterRepository perimeters) {
        return new ProposalStore(diff, ids, clock, perimeters);
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
            ProposalStore proposals, ClockPort clock, IdGenerator ids, InterviewRepository interviews,
            SourceUpdates sourceUpdates) {
        return new AcceptAiEdit(sessions, versions, tickets, proposals, clock, ids, interviews, sourceUpdates);
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
            ProjectRepository projects, KnowledgeIndex index, AgentJournal journal, AgentTasks agentTasks,
            TicketLinkRepository ticketLinks) {
        return new GetFeature(sessions, versions, tickets, new FeatureKnowledge(projects, index), journal, agentTasks,
                index, ticketLinks);
    }

    @Produces
    @ApplicationScoped
    SearchKnowledgeForAgent searchKnowledgeForAgent(KnowledgeQueries queries) {
        return new SearchKnowledgeForAgent(queries);
    }

    @Produces
    @ApplicationScoped
    AgentChangeStatus agentChangeStatus(SessionRepository sessions, ClockPort clock, AgentJournal journal,
            TaskRepository tasks) {
        return new AgentChangeStatus(sessions, clock, journal, tasks);
    }

    @Produces
    @ApplicationScoped
    ChangeStatus changeStatus(SessionRepository sessions, ClockPort clock, TaskRepository tasks) {
        return new ChangeStatus(sessions, clock, tasks);
    }

    @Produces
    @ApplicationScoped
    EvolveSpecification evolveSpecification(SessionRepository sessions, SpecificationRepository versions,
            ProjectRepository projects, StartSession start) {
        return new EvolveSpecification(sessions, versions, projects, start);
    }

    @Produces
    @ApplicationScoped
    ListProjectIssues listProjectIssues(ProjectRepository projects, JiraAccess jira, ListedIssues listed,
            SourceSyncRepository syncs) {
        return new ListProjectIssues(projects, jira, listed, syncs);
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
            OperationRegistry operations, PerimeterRepository perimeters) {
        return new PushToJira(tracker, jira, markup, llm, consent, sessions, versions, tickets, proposals, clock, ids,
                knowledge, operations, perimeters);
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
            KnowledgeIndex index, DocumentFolder folder, ConfluenceAccess confluence, ConfluenceClient client,
            ConfluencePageStates pages) {
        return new ManageDocSources(projects, docSources, ids, index, folder, confluence, client, pages);
    }

    // ---------- Confluence (016) ----------

    @Produces
    @ApplicationScoped
    ManageReliability manageReliability(ProjectRepository projects, ReliabilityRepository reliability) {
        return new ManageReliability(projects, reliability);
    }

    @Produces
    @ApplicationScoped
    ConfluenceAccess confluenceAccess(ConfluenceSettingsRepository settings, SecretStore secrets) {
        return new ConfluenceAccess(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    SaveConfluenceSettings saveConfluenceSettings(ConfluenceSettingsRepository settings, SecretStore secrets) {
        return new SaveConfluenceSettings(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    DeleteConfluenceSettings deleteConfluenceSettings(ConfluenceSettingsRepository settings, SecretStore secrets) {
        return new DeleteConfluenceSettings(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    TestConfluenceConnection testConfluenceConnection(ConfluenceClient client, ConfluenceSettingsRepository settings,
            SecretStore secrets, ClockPort clock) {
        return new TestConfluenceConnection(client, settings, secrets, clock);
    }

    @Produces
    @ApplicationScoped
    ConfluenceIndexation confluenceIndexation(ConfluenceAccess access, ConfluenceClient client,
            ConfluencePageStates pages, DocSourceRepository docSources, KnowledgeIndex index, ClockPort clock) {
        return new ConfluenceIndexation(access, client, pages, docSources, index, clock);
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
            TicketLinkRepository links, KnowledgeIndex index, EmbeddingPort embedding, ClockPort clock,
            ConfluenceIndexation confluence) {
        return new SynchronizeKnowledge(projects, sources, docSources, reader, folder, fileStates, architecture, jira,
                tracker, links, index, embedding, clock, confluence);
    }

    @Produces
    @ApplicationScoped
    Indexations indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, ClockPort clock,
            @ConfigProperty(name = "specwriter.knowledge.auto-index", defaultValue = "true") boolean automatic,
            SourceSyncRepository syncs, AppEvents events) {
        return new Indexations(synchronize, projects, task -> Thread.ofVirtual().name("indexation").start(task), clock,
                automatic, syncs, events);
    }

    @Produces
    @ApplicationScoped
    ManageNotifications manageNotifications(SettingsRepository settings) {
        return new ManageNotifications(settings);
    }

    /** Assistant de premier lancement (018) ; ouvert de lui-même sauf en e2e (research.md R5, R7). */
    @Produces
    @ApplicationScoped
    ManageOnboarding manageOnboarding(OnboardingRepository onboarding, SettingsRepository settings,
            ConsentRepository consents, ClockPort clock,
            @ConfigProperty(name = "specwriter.onboarding.auto-open", defaultValue = "true") boolean autoOpen) {
        return new ManageOnboarding(onboarding, settings, consents, clock, autoOpen);
    }

    @Produces
    @ApplicationScoped
    AutoSync autoSync(SourceSyncRepository syncs, Indexations indexations, ClockPort clock,
            @ConfigProperty(name = "specwriter.knowledge.auto-sync.minute") java.time.Duration minute) {
        return new AutoSync(syncs, indexations, clock, minute);
    }

    @Produces
    @ApplicationScoped
    ManageSourceSync manageSourceSync(SourceSyncRepository syncs, Indexations indexations,
            @ConfigProperty(name = "specwriter.knowledge.auto-sync.minute") java.time.Duration minute) {
        return new ManageSourceSync(syncs, indexations, minute);
    }

    @Produces
    @ApplicationScoped
    ManageEmbeddingSettings manageEmbeddingSettings(SettingsRepository settings, EmbeddingPort embedding) {
        return new ManageEmbeddingSettings(settings, embedding);
    }

    @Produces
    @ApplicationScoped
    ManageBrowser manageBrowser(SettingsRepository settings, BrowserPort browsers) {
        return new ManageBrowser(settings, browsers);
    }

    @Produces
    @ApplicationScoped
    fr.kawamind.application.knowledge.OpenSourceDocument openSourceDocument(DocSourceRepository docSources,
            ManageBrowser browser, BrowserPort browsers) {
        return new fr.kawamind.application.knowledge.OpenSourceDocument(docSources,
                new fr.kawamind.infrastructure.platform.LocalFileLocator(), browser, browsers);
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
            EmbeddingPort embedding, Indexations indexations, JiraAccess jira, ProvenanceResolver provenance) {
        return new KnowledgeQueries(projects, index, sources, embedding, indexations, jira, provenance);
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
