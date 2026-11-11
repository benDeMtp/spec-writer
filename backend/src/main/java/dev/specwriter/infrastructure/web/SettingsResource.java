package dev.specwriter.infrastructure.web;

import dev.specwriter.application.knowledge.ManageEmbeddingSettings;
import dev.specwriter.application.settings.DeleteJiraSettings;
import dev.specwriter.application.settings.DisconnectCopilot;
import dev.specwriter.application.settings.PollCopilotConnection;
import dev.specwriter.application.settings.SaveJiraSettings;
import dev.specwriter.application.settings.SelectModel;
import dev.specwriter.application.settings.StartCopilotConnection;
import dev.specwriter.application.settings.TestJiraConnection;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.port.GitHubAuthenticator.ConnectionStart;
import dev.specwriter.domain.port.GitHubAuthenticator.State;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.ModelCapacity;
import dev.specwriter.domain.settings.SecretPersistence;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/** {@code /api/settings/**} (contracts/openapi.yaml). Aucun secret n'est jamais renvoyé (FR-033). */
@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
public class SettingsResource {

    /**
     * Vue Copilot ; {@code models} donne la capacité, le coefficient et la place de la connaissance
     * de chaque modèle autorisé, {@code knowledgeChars} celle du modèle choisi (008, contracts/api.md).
     */
    public record CopilotView(String state, String login, SecretPersistence secretPersistence, boolean consent,
            String model, List<String> availableModels, List<ModelView> models, int knowledgeChars) {
    }

    /** Capacité d'un modèle : valeurs nulles si inconnues. */
    public record ModelView(String id, String name, Integer promptTokens, Integer contextTokens, Double multiplier,
            int knowledgeChars) {

        static ModelView of(ModelCapacity c) {
            return new ModelView(c.id(), c.name(), c.promptTokens(), c.contextTokens(), c.multiplier(),
                    KnowledgeBudget.forPromptTokens(c.prompt()).total());
        }
    }

    public record LastTestView(Instant at, String result) {
    }

    public record JiraView(boolean enabled, String serverUrl, String defaultProjectKey, String caCertificatePath,
            boolean tokenSet, SecretPersistence secretPersistence, boolean insecureHttp, LastTestView lastTest,
            boolean consent) {
    }

    public record SettingsView(CopilotView copilot, JiraView jira) {
    }

    public record PollView(State state, String login, SecretPersistence secretPersistence) {
    }

    public record ModelRequest(String model) {
    }

    public record JiraRequest(String serverUrl, String defaultProjectKey, String token, String caCertificatePath,
            boolean acceptInsecureHttp) {

        @Override
        public String toString() {
            return "JiraRequest[serverUrl=" + serverUrl + ", token=" + (token == null ? "absent" : "***") + "]";
        }
    }

    /** {@code projectKey} : projet Jira vérifié, celui du projet courant ; null sans clé (012). */
    public record TestView(String result, String serverVersion, String userMessage, String projectKey) {
    }

    @Inject
    SettingsRepository settings;
    @Inject
    SecretStore secrets;
    @Inject
    ConsentPolicy consents;
    @Inject
    StartCopilotConnection startCopilot;
    @Inject
    PollCopilotConnection pollCopilot;
    @Inject
    DisconnectCopilot disconnectCopilot;
    @Inject
    SelectModel selectModel;
    @Inject
    SaveJiraSettings saveJira;
    @Inject
    DeleteJiraSettings deleteJira;
    @Inject
    TestJiraConnection testJira;
    @Inject
    ManageEmbeddingSettings embedding;

    /** Dossier de remplacement du modèle de recherche, pooling et préfixes (research.md R19 de 002). */
    public record EmbeddingInput(String folder, String pooling, String queryPrefix, String passagePrefix) {
    }

    @GET
    public SettingsView get() {
        return new SettingsView(copilotView(settings.copilot()), settings.jira().map(this::jiraView).orElse(null));
    }

    @POST
    @Path("/copilot/device-flow")
    public ConnectionStart startConnection() {
        return startCopilot.run();
    }

    @GET
    @Path("/copilot/device-flow/{flowId}")
    public PollView poll(@PathParam("flowId") String flowId) {
        var status = pollCopilot.run(flowId);
        return new PollView(status.state(), status.login(), SecretPersistence.from(secrets.persistence()));
    }

    @PUT
    @Path("/copilot/model")
    @Consumes(MediaType.APPLICATION_JSON)
    public void selectModel(ModelRequest request) {
        if (request == null || request.model() == null || request.model().isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez un modèle.");
        }
        selectModel.run(request.model());
    }

    @DELETE
    @Path("/copilot")
    public void disconnect() {
        disconnectCopilot.run();
    }

    @PUT
    @Path("/jira")
    @Consumes(MediaType.APPLICATION_JSON)
    public JiraView saveJira(JiraRequest request) {
        if (request == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return jiraView(saveJira.run(new SaveJiraSettings.Command(request.serverUrl(), request.defaultProjectKey(),
                request.token(), request.caCertificatePath(), request.acceptInsecureHttp())));
    }

    @DELETE
    @Path("/jira")
    public void deleteJira() {
        deleteJira.run();
    }

    @GET
    @Path("/embedding")
    public ManageEmbeddingSettings.View embedding() {
        return embedding.get();
    }

    @PUT
    @Path("/embedding")
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageEmbeddingSettings.View saveEmbedding(EmbeddingInput input) {
        EmbeddingInput in = input == null ? new EmbeddingInput(null, null, null, null) : input;
        return embedding.save(in.folder(), in.pooling(), in.queryPrefix(), in.passagePrefix());
    }

    @POST
    @Path("/jira/test")
    public TestView testJira() {
        var outcome = testJira.run();
        return new TestView(outcome.result().name(), outcome.serverVersion(), outcome.userMessage(),
                outcome.projectKey());
    }

    private CopilotView copilotView(CopilotSettings c) {
        String state;
        if (c.connectedLogin() == null) {
            state = "NOT_CONFIGURED";
        } else if (c.subscriptionState() == CopilotSettings.SubscriptionState.NO_COPILOT) {
            state = "NO_COPILOT";
        } else {
            state = "CONNECTED";
        }
        List<ModelView> models = c.availableModels().stream().map(id -> ModelView.of(c.capacities().stream()
                .filter(m -> m.id().equals(id)).findFirst().orElseGet(() -> new ModelCapacity(id, null, null, null, null))))
                .toList();
        int knowledge = KnowledgeBudget.forPromptTokens(c.capacity().map(ModelCapacity::prompt)
                .orElse(OptionalInt.empty())).total();
        return new CopilotView(state, c.connectedLogin(), SecretPersistence.from(secrets.persistence()),
                consents.isGranted(ConsentService.COPILOT), c.model(), c.availableModels(), models, knowledge);
    }

    private JiraView jiraView(JiraSettings j) {
        LastTestView last = j.lastTest() == null ? null
                : new LastTestView(j.lastTest().at(), j.lastTest().result().name());
        return new JiraView(j.enabled(), j.serverUrl(), j.defaultProjectKey(), j.caCertificatePath(),
                secrets.get(SecretStore.JIRA_PAT).isPresent(), j.secretPersistence(), j.insecureHttp(), last,
                consents.isGranted(ConsentService.JIRA));
    }
}
