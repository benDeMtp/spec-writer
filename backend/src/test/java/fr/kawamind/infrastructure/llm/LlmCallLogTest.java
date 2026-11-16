package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Journal des appels au modèle (014, FR-008) : opération, durée, tentative, outils ; jamais de contenu. */
class LlmCallLogTest {

    private static final String SECRET = "Payer en ligne avec le mot de passe hunter2";

    private final List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
    private final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(LlmCallLog.class.getName());
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void listen() {
        logger.addHandler(capture);
    }

    @AfterEach
    void stop() {
        logger.removeHandler(capture);
    }

    private static ChatModel model(Function<ChatRequest, ChatResponse> answer) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return answer.apply(request);
            }

            @Override
            public List<ChatModelListener> listeners() {
                return List.of(new LlmCallLog());
            }
        };
    }

    private List<String> lines() {
        return logged.stream().map(r -> new SimpleFormatter().formatMessage(r)).toList();
    }

    @Test
    void operationDureeEtTentative() {
        model(r -> ChatResponse.builder().aiMessage(AiMessage.from("{\"secret\":\"" + SECRET + "\"}")).build())
                .chat(SystemMessage.from("[operation:generate-specification]\nConsignes"), UserMessage.from(SECRET));
        assertThat(lines()).singleElement().asString()
                .matches("LLM generate-specification : \\d+ ms, 1 tentative\\(s\\)");
        assertThat(lines()).noneMatch(l -> l.contains("hunter2") || l.contains("Consignes"));
    }

    @Test
    void uneRepriseCompteCommeUneTentativeDePlus() {
        model(r -> ChatResponse.builder().aiMessage(AiMessage.from("{}")).build())
                .chat(SystemMessage.from("[operation:propose-questions]"), UserMessage.from(SECRET),
                        AiMessage.from("pas du JSON"), UserMessage.from("La réponse précédente n'était pas conforme."));
        assertThat(lines()).singleElement().asString().matches("LLM propose-questions : \\d+ ms, 2 tentative\\(s\\)");
    }

    @Test
    void lesOutilsDemandesSontNommesSansLeursArguments() {
        model(r -> ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                ToolExecutionRequest.builder().id("1").name("search_knowledge").arguments("{\"query\":\"hunter2\"}").build(),
                ToolExecutionRequest.builder().id("2").name("analyze_perimeter").arguments("{}").build()))).build())
                .chat(SystemMessage.from("[operation:converse-agent]"), UserMessage.from(SECRET));
        assertThat(lines()).singleElement().asString()
                .matches("LLM converse-agent : \\d+ ms, 1 tentative\\(s\\), outils : search_knowledge, analyze_perimeter");
        assertThat(lines()).noneMatch(l -> l.contains("hunter2"));
    }

    @Test
    void lEchecEstJournaliseAvecLaTentativeEtLaCauseSansContenu() {
        DomainException failure = new DomainException(ErrorCode.LLM_UNAVAILABLE, "indisponible",
                new IllegalStateException("UNAVAILABLE pendant l'envoi du message"));
        ChatModel failing = model(r -> {
            throw failure;
        });
        assertThatThrownBy(() -> failing.chat(SystemMessage.from("[operation:generate-specification]"),
                UserMessage.from(SECRET))).isSameAs(failure);
        assertThat(logged).singleElement().satisfies(r -> {
            assertThat(r.getLevel()).isEqualTo(Level.WARNING);
            assertThat(new SimpleFormatter().formatMessage(r))
                    .matches("LLM generate-specification : échec à la tentative 1 après \\d+ ms \\(LLM_UNAVAILABLE\\)");
            assertThat(r.getThrown()).isSameAs(failure);
        });
    }

    @Test
    void sansMarqueurLOperationEstInconnue() {
        model(r -> ChatResponse.builder().aiMessage(AiMessage.from("ok")).build())
                .chat(UserMessage.from(SECRET));
        assertThat(lines()).singleElement().asString().startsWith("LLM inconnue : ");
    }
}
