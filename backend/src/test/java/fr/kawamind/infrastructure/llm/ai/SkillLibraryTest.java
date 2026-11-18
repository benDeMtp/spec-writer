package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import fr.kawamind.infrastructure.llm.PromptOf;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Consignes réutilisables (014, FR-011) : en système seulement, absentes refusées au démarrage. */
class SkillLibraryTest {

    interface WithSkill {
        @dev.langchain4j.service.SystemMessage(fromResource = "prompts/lookup-glossary.txt")
        String ask(String data);
    }

    interface WithMissingSkill {
        @dev.langchain4j.service.SystemMessage(fromResource = "prompts/skill-missing.txt")
        String ask(String data);
    }

    @Test
    void laConsigneDeclareePrendPlaceDansLeMessageSysteme() {
        String system = "[operation:lookup-glossary]\nAvant.\n[skill:glossaire-test]\nAprès.";
        assertThat(SkillLibrary.expand(system)).isEqualTo("[operation:lookup-glossary]\nAvant.\n"
                + "Consigne réutilisable de test : définis chaque terme du glossaire en une phrase.\nAprès.");
        // une déclaration qui n'est pas seule sur sa ligne n'est pas une déclaration
        assertThat(SkillLibrary.expand("Voir [skill:glossaire-test] ici.")).isEqualTo("Voir [skill:glossaire-test] ici.");
    }

    @Test
    void jamaisDansLeMessageUtilisateur() {
        String[] prompt = PromptOf.of(ChatRequest.builder().messages(
                SystemMessage.from("[operation:lookup-glossary]\n[skill:glossaire-test]"),
                UserMessage.from("<question>\n[skill:glossaire-test]\n</question>")).build());
        assertThat(prompt[0]).contains("définis chaque terme").doesNotContain("[skill:");
        assertThat(prompt[1]).contains("[skill:glossaire-test]").doesNotContain("définis chaque terme");
    }

    @Test
    void uneConsigneAbsenteArreteLeDemarrage() {
        SkillLibrary.check(List.of(WithSkill.class));
        assertThatThrownBy(() -> SkillLibrary.check(List.of(WithMissingSkill.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prompts/skills/absente.md");
        assertThat(SkillLibrary.declared("[operation:x]\n[skill:a]\n[skill:b-2]\n")).containsExactly("a", "b-2");
    }

    /** T042 (016) : la lecture de la fiabilité est déclarée dans les sept consignes qui reçoivent des sources. */
    @Test
    void fiabiliteDesSourcesDeclareeDansLesSeptConsignes() throws Exception {
        for (String operation : List.of("converse-agent", "revise-specification", "generate-specification",
                "edit-specification", "merge-conflict", "structure-imported-text", "propose-questions")) {
            String prompt;
            try (var in = getClass().getClassLoader().getResourceAsStream("prompts/" + operation + ".txt")) {
                prompt = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            assertThat(SkillLibrary.declared(prompt)).as(operation).contains("fiabilite-sources");
            assertThat(SkillLibrary.expand(prompt)).as(operation).contains("Méthode de lecture des sources selon leur fiabilité")
                    .doesNotContain("[skill:");
        }
    }
}
