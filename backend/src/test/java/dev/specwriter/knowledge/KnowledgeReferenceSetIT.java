package dev.specwriter.knowledge;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.application.knowledge.StoryFamily;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T118 : jeu de référence réel, hors du dépôt (research.md R27). Lancé à la main :
 * {@code ./mvnw -pl backend test -Preference -Dspecwriter.reference-set=<dossier>} ; s'arrête
 * proprement si le dossier est absent. Mesure SC-002 (tickets parmi les cinq premiers ≥ 90 %, ADR,
 * flux manquants et efforts à 100 % sans faux positif) et SC-004a (durée de l'indexation, tickets
 * proches en moins de 5 s) pour chaque modèle déclaré dans {@code set.json}, et écrit le rapport
 * dans le dossier du jeu. SC-003 et SC-004b, qui demandent le vrai Copilot, relèvent de {@code -Peval}.
 */
@QuarkusTest
@Tag("reference")
class KnowledgeReferenceSetIT {

    static final String PROPERTY = "specwriter.reference-set";
    private static final Logger LOG = Logger.getLogger(KnowledgeReferenceSetIT.class);

    @Inject
    ProjectRepository projects;
    @Inject
    KnowledgeIndex index;
    @Inject
    EmbeddingPort embedding;
    @Inject
    FindRelatedIssues related;
    @Inject
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    LinkedTicketRepository tickets;
    @Inject
    StoryFamily families;
    @Inject
    JiraAccess jira;
    @Inject
    IssueTracker tracker;
    @ConfigProperty(name = "specwriter.knowledge.subtask-min-z")
    double minZ;

    /** Dossier du jeu, ou arrêt propre du test s'il n'est pas donné. */
    static Path referenceSet() {
        String location = System.getProperty(PROPERTY, "");
        Assumptions.assumeTrue(!location.isBlank() && Files.isRegularFile(Path.of(location, "cases.json")),
                "Jeu de référence absent : indiquez -D" + PROPERTY + "=<dossier contenant cases.json>");
        return Path.of(location);
    }

    @Test
    void mesuresParModele() throws Exception {
        Path dir = referenceSet();
        Map<String, Object> initial = api().get("/api/settings/embedding").then().statusCode(200).extract().as(Map.class);
        List<Map<String, Object>> report = new ArrayList<>();
        try (ReferenceSet set = new ReferenceSet(dir)) {
            List<ReferenceSet.Model> models = set.settings.models().isEmpty()
                    ? List.of(new ReferenceSet.Model("actif", null, null, null, null))
                    : set.settings.models();
            boolean indexed = false;
            for (ReferenceSet.Model model : models) {
                if (model.folder() != null || !set.settings.models().isEmpty()) {
                    set.useModel(model);
                }
                JsonPath state = indexed ? set.reindex() : set.index();
                indexed = true;
                report.add(measure(set, model, state));
            }
        } finally {
            restore(initial);
        }
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path out = dir.resolve("report-" + Instant.now().toString().replace(':', '-') + ".json");
        json.writeValue(out.toFile(), report);
        LOG.infof("Jeu de référence : rapport écrit dans %s", out);

        // seuils mesurés sur le premier modèle déclaré (celui retenu par défaut)
        Map<String, Object> first = report.get(0);
        assertThat((double) first.get("ticketRecall")).as("SC-002 tickets").isGreaterThanOrEqualTo(0.9);
        assertThat((double) first.get("adrRecall")).as("SC-002 ADR").isEqualTo(1.0);
        assertThat((int) first.get("retiredAdrs")).as("ADR remplacés transmis").isZero();
        assertThat((double) first.get("flowsExact")).as("SC-002 flux manquants").isEqualTo(1.0);
        assertThat((double) first.get("effortsExact")).as("SC-002 efforts").isEqualTo(1.0);
        assertThat((long) first.get("slowestRelatedMs")).as("SC-004a tickets proches").isLessThan(5_000);
        assertThat((int) first.get("subtaskFamilyLeaks")).as("SC-004c famille de la story").isZero();
        assertThat((double) first.get("subtaskFlagged")).as("SC-004c sous-tâches signalées").isGreaterThanOrEqualTo(0.8);
    }

    private Map<String, Object> measure(ReferenceSet set, ReferenceSet.Model model, JsonPath state) {
        int expectedIssues = 0;
        int foundIssues = 0;
        int expectedAdrs = 0;
        int foundAdrs = 0;
        int retired = 0;
        int flowsExact = 0;
        int effortsExact = 0;
        long slowest = 0;
        List<Map<String, Object>> cases = new ArrayList<>();
        for (ReferenceSet.Case c : set.cases) {
            long start = System.nanoTime();
            related.candidates(java.util.UUID.fromString(set.projectId), c.need());
            slowest = Math.max(slowest, Duration.ofNanos(System.nanoTime() - start).toMillis());
            ReferenceSet.Measure m = set.measure(c, projects, index, embedding, related);
            expectedIssues += c.expectedIssues().size();
            foundIssues += c.expectedIssues().size() - m.missedIssues().size();
            expectedAdrs += c.expectedAdrs().size();
            foundAdrs += c.expectedAdrs().size() - m.missedAdrs().size();
            retired += m.retiredAdrs().size();
            flowsExact += m.flowsExact() ? 1 : 0;
            effortsExact += m.effortsExact() ? 1 : 0;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("need", c.need());
            one.put("shownIssues", m.shown());
            one.put("missedIssues", m.missedIssues());
            one.put("missedAdrs", m.missedAdrs());
            one.put("missedModules", m.missedModules());
            one.put("missingFlows", m.missingFlows());
            one.put("efforts", m.efforts());
            cases.add(one);
        }
        // sous-tâches de story (SC-004c), si le jeu en décrit
        ReferenceSet.SubtaskWorld world = new ReferenceSet.SubtaskWorld(projects, sessions, versions, tickets, families,
                index, embedding, related, jira, tracker, minZ);
        int leaks = 0;
        double flagged = 0;
        List<Map<String, Object>> subtaskCases = new ArrayList<>();
        for (ReferenceSet.SubtaskCase c : set.subtaskCases) {
            ReferenceSet.SubtaskMeasure m = set.measureSubtasks(c, world);
            leaks += m.familyInSelection().size() + m.familyInRelated().size();
            flagged += m.flagged();
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("story", c.story());
            one.put("familyInSelection", m.familyInSelection());
            one.put("familyInRelated", m.familyInRelated());
            one.put("states", m.states());
            one.put("expected", c.expected());
            subtaskCases.add(one);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("model", model.name());
        out.put("indexationStatus", state == null ? null : state.getString("status"));
        out.put("indexationSeconds", state == null || state.getString("finishedAt") == null ? null
                : Duration.between(Instant.parse(state.getString("startedAt")), Instant.parse(state.getString("finishedAt")))
                        .toSeconds());
        out.put("ticketRecall", ratio(foundIssues, expectedIssues));
        out.put("adrRecall", ratio(foundAdrs, expectedAdrs));
        out.put("retiredAdrs", retired);
        out.put("flowsExact", ratio(flowsExact, set.cases.size()));
        out.put("effortsExact", ratio(effortsExact, set.cases.size()));
        out.put("slowestRelatedMs", slowest);
        out.put("cases", cases);
        out.put("subtaskFamilyLeaks", leaks);
        out.put("subtaskFlagged", set.subtaskCases.isEmpty() ? 1.0 : flagged / set.subtaskCases.size());
        out.put("subtaskCases", subtaskCases);
        LOG.infof("Jeu de référence, modèle %s : tickets %.2f, ADR %.2f, flux %.2f, efforts %.2f, indexation %s s",
                model.name(), out.get("ticketRecall"), out.get("adrRecall"), out.get("flowsExact"), out.get("effortsExact"),
                out.get("indexationSeconds"));
        return out;
    }

    private static double ratio(int found, int expected) {
        return expected == 0 ? 1.0 : (double) found / expected;
    }

    /** Le modèle de départ est remis, pour ne pas laisser un modèle de comparaison actif. */
    private static void restore(Map<String, Object> initial) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("folder", initial.getOrDefault("folder", ""));
        body.put("pooling", initial.get("pooling"));
        body.put("queryPrefix", initial.getOrDefault("queryPrefix", ""));
        body.put("passagePrefix", initial.getOrDefault("passagePrefix", ""));
        api().body(body).put("/api/settings/embedding");
    }
}
