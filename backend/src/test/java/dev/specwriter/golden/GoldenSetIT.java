package dev.specwriter.golden;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.application.specification.GenerateSpecification;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.scenario.ScenarioStructureValidator;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.TraceabilityValidator;
import dev.specwriter.domain.specification.UseCase;
import dev.specwriter.infrastructure.llm.LangChainLlmClient;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.InMemorySpecifications;
import dev.specwriter.support.ScriptedChatModel;
import dev.specwriter.support.SequentialIdGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
class GoldenSetIT {

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
        LangChainLlmClient llm = new LangChainLlmClient(model);

        SessionSnapshot s = new StartSession(sessions, new InMemoryProjects(), llm, policy, clock, ids)
                .run(Files.readString(dir.resolve("need.md")), null);
        Deque<JsonNode> answers = new ArrayDeque<>();
        JSON.readTree(Files.readString(dir.resolve("answers.json"))).forEach(answers::add);
        AnswerQuestion answer = new AnswerQuestion(sessions, llm, policy, clock, ids);
        while (!s.questionnaire().currentBatch().isEmpty()) {
            Question q = s.questionnaire().currentBatch().getFirst();
            JsonNode a = answers.poll();
            assertThat(a).as("réponse prévue pour « %s »", q.label()).isNotNull();
            s = answer.run(s.session().id(), q.id(), a.path("value").asString(""), a.path("skip").asBoolean(false));
        }
        assertThat(answers).as("réponses non utilisées").isEmpty();
        new ConcludeQuestionnaire(sessions, clock).run(s.session().id());
        SpecificationVersion version = new GenerateSpecification(sessions, versions, llm, policy, clock, ids, "golden")
                .run(s.session().id()).version();

        assertThat(model.exhausted()).as("tous les appels LLM prévus ont eu lieu").isTrue();
        SpecificationContent c = version.content();
        Set<java.util.UUID> treated = sessions.questionnaire(s.session().id()).orElseThrow().treated().stream()
                .map(Question::id).collect(Collectors.toSet());
        assertThat(ScenarioStructureValidator.validate(c).messages()).isEmpty();
        assertThat(TraceabilityValidator.validate(c, treated).messages()).isEmpty();
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
}
