package dev.specwriter.domain.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.RequiredField.Kind;
import dev.specwriter.domain.jira.RequiredField.Option;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequiredFieldTest {

    private static RequiredField field(Kind kind) {
        return new RequiredField("customfield_1", "Équipe", kind, List.of(new Option("1", "Alpha")));
    }

    @Test
    void chaqueTypeProduitLaValeurAttendueParJira() {
        assertThat(field(Kind.TEXT).toJiraValue("  Texte ")).isEqualTo("Texte");
        assertThat(field(Kind.NUMBER).toJiraValue("3")).isEqualTo(3L);
        assertThat(field(Kind.NUMBER).toJiraValue("2.5")).isEqualTo(2.5d);
        assertThat(field(Kind.DATE).toJiraValue("2026-11-02")).isEqualTo("2026-11-02");
        assertThat(field(Kind.OPTION).toJiraValue("1")).isEqualTo(Map.of("id", "1"));
        assertThat(field(Kind.OPTIONS).toJiraValue("1")).isEqualTo(List.of(Map.of("id", "1")));
    }

    @Test
    void saisieAbsenteOuInvalideRefuseeEnNommantLeChamp() {
        assertThat(reason(Kind.TEXT, " ")).isEqualTo("Le champ Jira « Équipe » est obligatoire.");
        assertThat(reason(Kind.TEXT, null)).contains("est obligatoire");
        assertThat(reason(Kind.NUMBER, "trois")).contains("doit être un nombre");
        assertThat(reason(Kind.DATE, "02/11/2026")).contains("AAAA-MM-JJ");
        assertThat(reason(Kind.OPTION, "9")).contains("valeurs proposées");
        assertThat(reason(Kind.UNSUPPORTED, "x")).contains("renseignez-le dans Jira");
    }

    private static String reason(Kind kind, String input) {
        DomainException e = catchThrowableOfType(DomainException.class, () -> field(kind).toJiraValue(input));
        assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        return e.userMessage();
    }
}
