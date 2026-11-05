package dev.specwriter.knowledge;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.AnalyzeArchitecture;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssues;
import dev.specwriter.application.knowledge.ReportSubtasks;
import dev.specwriter.application.knowledge.SelectKnowledge;
import dev.specwriter.application.knowledge.StoryFamily;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.ImpactResult;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.UseCase;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.JiraWorld;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jeu de référence de la connaissance (research.md R27) : un dossier avec {@code cases.json}, les
 * sources figées ({@code jira/}, {@code antora/}, {@code c4/}) et {@code set.json} (clé Jira,
 * chemins, ownership, modèles à comparer). Le jeu est indexé par l'API, avec Jira simulé ; chaque
 * besoin est mesuré sans LLM : l'IA d'analyse d'architecture est remplacée par un oracle qui
 * choisit les modules et échanges attendus, l'outil calcule le reste.
 */
final class ReferenceSet implements AutoCloseable {

    /** Rang des tickets montrés à l'utilisateur (SC-002). */
    static final int SHOWN = 5;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ADR_NUMBER = Pattern.compile("adr[-_ ]?0*(\\d+)", Pattern.CASE_INSENSITIVE);

    record Case(String need, List<String> expectedIssues, List<String> expectedAdrs, List<String> expectedModules,
            List<List<String>> exchanges, List<List<String>> expectedMissingFlows, List<String> expectedEfforts) {

        Case {
            expectedIssues = orEmpty(expectedIssues);
            expectedAdrs = orEmpty(expectedAdrs);
            expectedModules = orEmpty(expectedModules);
            exchanges = orEmpty(exchanges);
            expectedMissingFlows = orEmpty(expectedMissingFlows);
            expectedEfforts = orEmpty(expectedEfforts);
        }
    }

    /** Modèle d'embedding à comparer : dossier local, ou modèle par défaut si {@code folder} est vide. */
    record Model(String name, String folder, String pooling, String queryPrefix, String passagePrefix) {
    }

    /** Cas d'usage d'une version, pour les sous-tâches. */
    record UseCaseSpec(String code, String title, String goal, List<String> steps) {

        UseCase toUseCase() {
            return new UseCase(code, title, "client", goal, List.of(), orEmpty(steps), List.of(), null);
        }
    }

    /**
     * Sous-tâches d'une story (research.md R29) : la spécification liée à la story passe de
     * {@code previousUseCases} à {@code useCases} ; {@code expected} donne l'état attendu de chaque
     * sous-tâche ({@code TO_REVIEW}, {@code DELIVERED_TO_REWORK}, {@code CONSISTENT}, {@code UNMATCHED}).
     */
    record SubtaskCase(String story, String need, List<UseCaseSpec> previousUseCases, List<UseCaseSpec> useCases,
            Map<String, String> expected) {

        SubtaskCase {
            previousUseCases = orEmpty(previousUseCases);
            useCases = orEmpty(useCases);
            expected = expected == null ? Map.of() : Map.copyOf(expected);
        }
    }

    /** Ce que l'outil fait des sous-tâches : famille dans la sélection ou les tickets proches, et états. */
    record SubtaskMeasure(SubtaskCase expected, List<String> familyInSelection, List<String> familyInRelated,
            Map<String, String> states) {

        /** Part des sous-tâches attendues « à revoir » ou « livrée, à reprendre » qui le sont (SC-004c). */
        double flagged() {
            List<String> touched = expected.expected().entrySet().stream()
                    .filter(e -> e.getValue().equals("TO_REVIEW") || e.getValue().equals("DELIVERED_TO_REWORK"))
                    .map(Map.Entry::getKey).toList();
            if (touched.isEmpty()) {
                return 1;
            }
            return touched.stream().filter(k -> expected.expected().get(k).equals(states.get(k))).count()
                    / (double) touched.size();
        }
    }

    record Settings(String jiraProjectKey, String jira, String antora, String workspace, String ownership,
            List<Model> models, Double subtaskMinZ) {

        Settings {
            jiraProjectKey = jiraProjectKey == null ? "FACT" : jiraProjectKey;
            jira = jira == null ? "jira/issues.json" : jira;
            antora = antora == null ? "antora" : antora;
            models = orEmpty(models);
        }
    }

    /** Ce que l'outil retrouve pour un besoin. */
    record Measure(Case expected, List<String> candidates, List<String> adrs, List<String> retiredAdrs,
            List<String> moduleCandidates, List<String> modules, List<List<String>> missingFlows, List<String> efforts) {

        List<String> shown() {
            return candidates.subList(0, Math.min(SHOWN, candidates.size()));
        }

        List<String> missedIssues() {
            return expected.expectedIssues().stream().filter(k -> !shown().contains(k)).toList();
        }

        List<String> missedAdrs() {
            return expected.expectedAdrs().stream().filter(a -> adrs.stream().noneMatch(s -> sameAdr(a, s))).toList();
        }

        List<String> missedModules() {
            return expected.expectedModules().stream().filter(m -> !modules.contains(m)).toList();
        }

        boolean flowsExact() {
            return Set.copyOf(missingFlows).equals(Set.copyOf(expected.expectedMissingFlows()));
        }

        boolean effortsExact() {
            return Set.copyOf(efforts).equals(Set.copyOf(expected.expectedEfforts()));
        }
    }

    final Path dir;
    final Settings settings;
    final List<Case> cases;
    final List<SubtaskCase> subtaskCases;
    private final WireMockServer jira;
    String projectId;

    ReferenceSet(Path dir) throws IOException {
        this.dir = dir;
        Path set = dir.resolve("set.json");
        this.settings = Files.exists(set) ? JSON.readValue(set.toFile(), Settings.class)
                : new Settings(null, null, null, null, null, null, null);
        this.cases = JSON.readValue(dir.resolve("cases.json").toFile(), new TypeReference<List<Case>>() {
        });
        Path subtasks = dir.resolve("subtasks.json");
        this.subtaskCases = Files.exists(subtasks) ? JSON.readValue(subtasks.toFile(),
                new TypeReference<List<SubtaskCase>>() {
                }) : List.of();
        this.jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
    }

    @Override
    public void close() {
        if (projectId != null) {
            api().delete("/api/projects/" + projectId + "/indexations/current");
            api().delete("/api/projects/" + projectId + "?sessions=DELETE");
        }
        jira.stop();
    }

    /** Crée le projet, déclare les sources et lance une indexation complète ; renvoie son état final. */
    JsonPath index() throws IOException {
        String key = settings.jiraProjectKey();
        jira.resetAll();
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/" + key)).willReturn(okJson("{\"key\":\"" + key + "\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching(".*ORDER BY updated ASC"))
                .willReturn(okJson(issues(false))));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("fields", equalTo("key"))
                .willReturn(okJson(issues(true))));
        stubStatuses();
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", key, "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        api().body(Map.of("granted", true)).put("/api/consents/JIRA").then().statusCode(204);
        projectId = api().body(Map.of("name", "Référence " + UUID.randomUUID(), "jiraProjectKey", key))
                .post("/api/projects").then().statusCode(201).extract().path("id");
        if (Files.isDirectory(dir.resolve(settings.antora()))) {
            api().body(Map.of("kind", "FOLDER", "location", dir.resolve(settings.antora()).toString()))
                    .post(base() + "/doc-sources").then().statusCode(201);
        }
        if (settings.workspace() != null) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("workspace", dir.resolve(settings.workspace()).toString());
            if (settings.ownership() != null) {
                body.put("ownership", settings.ownership());
            }
            api().body(body).put(base() + "/architecture-source").then().statusCode(200);
        }
        return reindex();
    }

    /** Indexation complète, attendue jusqu'à sa fin (10 minutes au plus, SC-004a). */
    JsonPath reindex() {
        waitIdle();
        api().body(Map.of("mode", "FULL")).post(base() + "/indexations").then().statusCode(202);
        return waitIdle();
    }

    private JsonPath waitIdle() {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        while (true) {
            var response = api().get(base() + "/indexations/current");
            if (response.statusCode() == 404) {
                return null;
            }
            JsonPath state = response.then().statusCode(200).extract().jsonPath();
            if (!"RUNNING".equals(state.getString("status")) || System.nanoTime() > deadline) {
                return state;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return state;
            }
        }
    }

    /** Remplace le modèle d'embedding ; dossier vide : modèle par défaut. */
    void useModel(Model model) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("folder", model.folder() == null ? "" : model.folder());
        if (model.pooling() != null) {
            body.put("pooling", model.pooling());
        }
        body.put("queryPrefix", model.queryPrefix() == null ? "" : model.queryPrefix());
        body.put("passagePrefix", model.passagePrefix() == null ? "" : model.passagePrefix());
        api().body(body).put("/api/settings/embedding").then().statusCode(200);
    }

    String base() {
        return "/api/projects/" + projectId;
    }

    /** Requêtes JQL reçues par le Jira simulé, dans l'ordre. */
    List<String> jqlReceived() {
        return jira.getAllServeEvents().stream().map(e -> e.getRequest().queryParameter("jql"))
                .filter(q -> q.isPresent()).map(q -> q.firstValue()).toList().reversed();
    }

    /** Mesure un besoin : tickets candidats, ADR transmis, modules et faits calculés par l'outil. */
    Measure measure(Case c, ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            FindRelatedIssues related) {
        UUID project = UUID.fromString(projectId);
        List<String> candidates = related.candidates(project, c.need()).stream().map(KnowledgeItem::ref).toList();

        JiraWorld oracle = new JiraWorld().grant(ConsentService.COPILOT);
        oracle.llm.impacts.add(new ImpactResult(c.expectedModules(), c.exchanges().stream()
                .map(p -> new ImpactResult.Exchange(p.get(0), p.get(1), "attendu")).toList()));
        AnalyzeArchitecture analyze = new AnalyzeArchitecture(index, oracle.llm, oracle.policy);
        Session session = Session.start(UUID.randomUUID(), project, Need.of(c.need()), null, Instant.now());
        ProjectKnowledge k = new SelectKnowledge(projects, index, embedding, analyze).forSpecification(session, List.of());

        List<String> adrs = new ArrayList<>();
        List<String> retired = new ArrayList<>();
        for (ProjectKnowledge.Entry e : k.entries()) {
            if (e.kind() == KnowledgeKind.ADR) {
                adrs.add(e.title() + " " + e.ref());
                if (AdrStatus.SUPERSEDED.name().equals(e.status()) || AdrStatus.DEPRECATED.name().equals(e.status())) {
                    retired.add(e.ref());
                }
            }
        }
        List<String> moduleCandidates = oracle.llm.impactRequests.isEmpty() ? List.of()
                : oracle.llm.impactRequests.get(0).candidates().stream().map(el -> el.name()).toList();
        List<String> modules = k.modules().stream().map(m -> m.name()).toList();
        List<List<String>> missing = k.exchanges().stream().filter(x -> x.kind() == Exchange.Kind.MISSING)
                .map(x -> List.of(x.source(), x.destination())).toList();
        List<String> efforts = k.deploymentFacts().stream().map(ReferenceSet::effort).toList();
        return new Measure(c, candidates, adrs, retired, moduleCandidates, modules, missing, efforts);
    }

    static String effort(DeploymentFact f) {
        return f.kind().name() + ":" + f.element() + (f.other() == null ? "" : ">" + f.other());
    }

    /** Même ADR : même numéro (« ADR-005 », « adr-005.html »), sinon même texte. */
    static boolean sameAdr(String expected, String selected) {
        Matcher e = ADR_NUMBER.matcher(expected);
        if (e.find()) {
            Matcher s = ADR_NUMBER.matcher(selected);
            while (s.find()) {
                if (s.group(1).equals(e.group(1))) {
                    return true;
                }
            }
            return false;
        }
        return selected.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    /** Statut relu de chaque ticket du jeu ({@code fields=status,updated}). */
    @SuppressWarnings("unchecked")
    private void stubStatuses() throws IOException {
        List<Map<String, Object>> fixture = JSON.readValue(dir.resolve(settings.jira()).toFile(), List.class);
        for (Map<String, Object> i : fixture) {
            jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/" + i.get("key"))).willReturn(okJson(JSON.writeValueAsString(
                    Map.of("key", i.get("key"), "fields", Map.of("status", Map.of("name", i.get("status"),
                            "statusCategory", Map.of("key", i.get("statusCategory"))), "updated", i.get("updated")))))));
        }
    }

    /**
     * Mesure les sous-tâches d'une story : une spécification liée à la story, avec une version par
     * liste de cas d'usage ; la famille ne doit entrer ni dans la sélection ni dans les tickets proches.
     */
    SubtaskMeasure measureSubtasks(SubtaskCase c, SubtaskWorld w) {
        UUID project = UUID.fromString(projectId);
        Instant now = Instant.now();
        Session session = Session.start(UUID.randomUUID(), project, Need.of(c.need()), null, now).generated(now);
        w.sessions().save(session, Questionnaire.empty());
        int number = 1;
        for (List<UseCaseSpec> useCases : List.of(c.previousUseCases(), c.useCases())) {
            if (useCases.isEmpty()) {
                continue;
            }
            SpecificationContent content = new SpecificationContent("Sous-tâches", c.need(),
                    useCases.stream().map(UseCaseSpec::toUseCase).toList(), List.of(), List.of(), List.of());
            w.versions().save(new SpecificationVersion(UUID.randomUUID(), session.id(), number, content, "fp" + number,
                    VersionOrigin.AI_EDIT, "# " + number, now));
            number++;
        }
        w.tickets().save(new LinkedJiraTicket(session.id(), c.story(), "http://jira/browse/" + c.story(), "Story", 1, now,
                "h", now, now, LinkedJiraTicket.SyncState.IN_SYNC));
        StoryFamily.Family family = w.families().of(session);

        JiraWorld oracle = new JiraWorld();
        ProjectKnowledge k = new SelectKnowledge(w.projects(), w.index(), w.embedding(),
                new AnalyzeArchitecture(w.index(), oracle.llm, oracle.policy), w.families()).forQuestions(session, List.of());
        List<String> inSelection = k.entries().stream().filter(e -> e.kind() == KnowledgeKind.TICKET)
                .map(ProjectKnowledge.Entry::ref).filter(family::contains).toList();
        List<String> inRelated = w.related().find(session, w.projects().find(project).orElseThrow()).issues().stream()
                .map(RelatedIssues.Issue::key).filter(family::contains).toList();

        double minZ = settings.subtaskMinZ() == null ? w.minZ() : settings.subtaskMinZ();
        ReportSubtasks report = new ReportSubtasks(w.sessions(), w.versions(), w.families(), w.index(), w.embedding(),
                w.jira(), w.tracker(), minZ);
        Map<String, String> states = new LinkedHashMap<>();
        report.run(session.id(), number - 1).ifPresent(r -> r.subtasks().forEach(t -> states.put(t.key(), t.state().name())));
        return new SubtaskMeasure(c, inSelection, inRelated, states);
    }

    /** Beans de l'application qu'utilise la mesure des sous-tâches. */
    record SubtaskWorld(ProjectRepository projects, SessionRepository sessions, SpecificationRepository versions,
            LinkedTicketRepository tickets, StoryFamily families, KnowledgeIndex index, EmbeddingPort embedding,
            FindRelatedIssues related, JiraAccess jira, IssueTracker tracker, double minZ) {
    }

    /** Réponse de recherche Jira construite à partir des tickets figés du jeu. */
    @SuppressWarnings("unchecked")
    private String issues(boolean keysOnly) throws IOException {
        List<Map<String, Object>> fixture = JSON.readValue(dir.resolve(settings.jira()).toFile(), List.class);
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Map<String, Object> i : fixture) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("key", i.get("key"));
            if (!keysOnly) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("summary", i.get("summary"));
                fields.put("description", i.get("description"));
                fields.put("status", Map.of("name", i.get("status"), "statusCategory", Map.of("key", i.get("statusCategory"))));
                fields.put("issuetype", Map.of("name", i.getOrDefault("issueType", "Story")));
                fields.put("updated", i.get("updated"));
                links(i, fields);
                issue.put("fields", fields);
            }
            issues.add(issue);
        }
        return JSON.writeValueAsString(Map.of("startAt", 0, "maxResults", 50, "total", issues.size(), "issues", issues));
    }

    /** Parent, sous-tâches et liens du ticket figé, au format de Jira (research.md R29). */
    @SuppressWarnings("unchecked")
    private static void links(Map<String, Object> fixture, Map<String, Object> fields) {
        if (fixture.get("parent") != null) {
            fields.put("parent", Map.of("key", fixture.get("parent")));
        }
        List<String> subtasks = (List<String>) fixture.getOrDefault("subtasks", List.of());
        fields.put("subtasks", subtasks.stream().map(k -> Map.of("key", k)).toList());
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, String> l : (List<Map<String, String>>) fixture.getOrDefault("links", List.of())) {
            boolean outward = "OUTWARD".equals(l.get("direction"));
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("type", Map.of("name", l.get("type"), outward ? "outward" : "inward", l.get("label")));
            link.put(outward ? "outwardIssue" : "inwardIssue", Map.of("key", l.get("key")));
            links.add(link);
        }
        fields.put("issuelinks", links);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
