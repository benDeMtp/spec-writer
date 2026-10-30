package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.support.ScriptedChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayChatModelTest {

    @Test
    void choisitLaReponseSelonOperationEtContenu(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"),
                "{\"operation\":\"propose-questions\",\"userContains\":[\"salle\"],\"response\":{\"q\":1}}");
        Files.writeString(dir.resolve("02.json"),
                "{\"operation\":\"propose-questions\",\"userContains\":[],\"response\":{\"q\":2}}");
        ReplayChatModel model = new ReplayChatModel(dir);

        String a = model.chat(SystemMessage.from("[operation:propose-questions] x"), UserMessage.from("réserver une salle"))
                .aiMessage().text();
        String b = model.chat(SystemMessage.from("[operation:propose-questions] x"), UserMessage.from("autre"))
                .aiMessage().text();
        assertThat(a).isEqualTo("{\"q\":1}");
        assertThat(b).isEqualTo("{\"q\":2}");
        assertThatThrownBy(() -> model.chat(SystemMessage.from("[operation:inconnue]"), UserMessage.from("x")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void operationLueDansLeMarqueur() {
        assertThat(Operations.of("[operation:generate-specification]\nTu es…")).contains("generate-specification");
        assertThat(Operations.of("sans marqueur")).isEmpty();
    }

    @Test
    void scriptStrictementOrdonne() {
        ScriptedChatModel scripted = new ScriptedChatModel()
                .then("a", List.of("x"), "{\"n\":1}")
                .then("b", List.of(), "{\"n\":2}");
        assertThat(scripted.chat(SystemMessage.from("[operation:a]"), UserMessage.from("x")).aiMessage().text()).isEqualTo("{\"n\":1}");
        assertThatThrownBy(() -> scripted.chat(SystemMessage.from("[operation:a]"), UserMessage.from("x")))
                .isInstanceOf(AssertionError.class);
    }
}
