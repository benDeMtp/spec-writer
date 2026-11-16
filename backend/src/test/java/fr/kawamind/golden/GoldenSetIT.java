package fr.kawamind.golden;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.session.AnswerQuestion;
import fr.kawamind.application.session.ConcludeQuestionnaire;
import fr.kawamind.application.session.SessionSnapshot;
import fr.kawamind.application.session.StartSession;
import fr.kawamind.application.specification.GenerateSpecification;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.application.knowledge.AnalyzeArchitecture;
import fr.kawamind.application.knowledge.SelectKnowledge;
import fr.kawamind.application.knowledge.SessionKnowledge;
import fr.kawamind.application.specification.DraftValidation;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectContext;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.scenario.AcceptanceScenario;
import fr.kawamind.domain.scenario.ScenarioStructureValidator;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.TraceabilityValidator;
import fr.kawamind.domain.specification.UseCase;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySpecifications;
import fr.kawamind.support.ScriptedChatModel;
import fr.kawamind.support.SequentialIdGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden set (principe IV, SC-002, SC-003) : chaque cas rejoue le questionnaire et la génération
 * avec des réponses LLM enregistrées, puis compare la structure, les identifiants, la traçabilité
 * et le Markdown à {@code expected.md}. {@code -Dgolden.update=true} réécrit les fichiers attendus,
 * à relire avant de les committer.
 */
@io.quarkus.test.junit.QuarkusTest
class GoldenSetIT {

    /** Port LLM de l'application, sur le modèle scripté du cas (014 : AI Services). */
    @jakarta.inject.Inject
    fr.kawamind.domain.port.LlmClient llmClient;

    static final Path ROOT = Path.of("src/test/resources/golden");
    static final JsonMapper JSON = JsonMapper.builder().build();

    static Stream<Path> cases() throws IOException {
        return Files.list(ROOT).filter(Files::isDirectory).sorted();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void rejouerLeCas(Path dir) throws IOException {
        ScriptedChatModel model = ScriptedChatModel.fromDirectory(dir.resolve("llm"));
        InMemorySessions sessions = new InMemorySessions();
        InMemorySpecifications versions = new InMemorySpecifications();
        InMemoryConsents consents = new InMemoryConsents();
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        ConsentPolicy policy = new ConsentPolicy(consents);
        FixedClock clock = new FixedClock();
        SequentialIdGenerator ids = new SequentialIdGenerator();
        io.quarkus.test.junit.QuarkusMock.installMockForType(model, dev.langchain4j.model.chat.ChatModel.class);
        fr.kawamind.domain.port.LlmClient llm = llmClient;
        // general.md, s'il existe : la fiche générale du projet par défaut (feature 002, FR-008, FR-011) ;
        // knowledge.json, s'il existe : l'index de connaissance du projet (R24, T111)
        InMemoryProjects projects = new InMemoryProjects();
        String general = read(dir.resolve("general.md"));
        InMemoryKnowledgeIndex index = KnowledgeFixture.read(dir.resolve("knowledge.json"), Project.DEFAULT_ID);
        if (!general.isEmpty()) {
            projects.save(projects.find(Project.DEFAULT_ID).orElseThrow()
                    .withContext(ProjectContext.edited(general, Instant.EPOCH)));
        }
        if (!general.isEmpty() || Files.exists(dir.resolve("knowledge.json"))) {
            try (Stream<Path> files = Files.list(dir.resolve("llm"))) {
                for (Path f : files.toList()) {
                    JsonNode step = JSON.readTree(Files.readString(f));
                    if (!"identify-impact".equals(step.path("operation").asString())) {
                        assertThat(step.path("userContains").toString()).as("%s reçoit la connaissance", f.getFileName())
                                .contains("<connaissance-projet>");
                    }
                }
            }
        }
        SessionKnowledge knowledge = new SelectKnowledge(projects, index, new FakeEmbedding(),
                new AnalyzeArchitecture(index, llm, policy));

        SessionSnapshot s = new StartSession(sessions, projects, llm, policy, clock, ids, null, null, knowledge)
                .run(Files.readString(dir.resolve("need.md")), null);
        Deque<JsonNode> answers = new ArrayDeque<>();
        JSON.readTree(Files.readString(dir.resolve("answers.json"))).forEach(answers::add);
        AnswerQuestion answer = new AnswerQuestion(sessions, llm, policy, clock, ids, knowledge);
        while (!s.questionnaire().currentBatch().isEmpty()) {
            Question q = s.questionnaire().currentBatch().getFirst();
            JsonNode a = answers.poll();
            assertThat(a).as("réponse prévue pour « %s »", q.label()).isNotNull();
            s = answer.run(s.session().id(), q.id(), a.path("value").asString(""), a.path("skip").asBoolean(false));
        }
        assertThat(answers).as("réponses non utilisées").isEmpty();
        new ConcludeQuestionnaire(sessions, clock).run(s.session().id());
        SpecificationVersion version = new GenerateSpecification(sessions, versions, llm, policy, clock, ids, "golden",
                knowledge).run(s.session().id()).version();

        assertThat(model.exhausted()).as("tous les appels LLM prévus ont eu lieu").isTrue();
        SpecificationContent c = version.content();
        List<Question> treatedQuestions = sessions.questionnaire(s.session().id()).orElseThrow().treated();
        Set<java.util.UUID> treated = treatedQuestions.stream().map(Question::id).collect(Collectors.toSet());
        assertThat(ScenarioStructureValidator.validate(c).messages()).isEmpty();
        // l'analyse d'architecture est gardée pour la session : aucun nouvel appel
        ProjectKnowledge used = knowledge.forSpecification(s.session(), DraftValidation.answers(treatedQuestions));
        assertThat(TraceabilityValidator.validate(c, treated, used).messages()).isEmpty();
        assertThat(version.knowledge()).isEqualTo(used.selection());
        for (int i = 0; i < c.useCases().size(); i++) {
            assertThat(c.useCases().get(i).code()).isEqualTo("UC-%02d".formatted(i + 1));
        }
        for (AcceptanceScenario sc : c.scenarios()) {
            assertThat(sc.code()).startsWith("SC-" + sc.useCaseCode().substring(3) + "-");
            assertThat(c.useCases().stream().map(UseCase::code)).contains(sc.useCaseCode());
        }

        Path expected = dir.resolve("expected.md");
        if (Boolean.getBoolean("golden.update") || !Files.exists(expected)) {
            Files.writeString(expected, version.markdown());
        }
        assertThat(version.markdown()).isEqualTo(Files.readString(expected));
    }

    static String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file) : "";
    }
}
