package dev.specwriter.infrastructure.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** T097 : la saisie reste un littéral JQL (OWASP A03). */
class JqlBuilderTest {

    @Test
    void requeteDeBase() {
        assertThat(JqlBuilder.textSearch("PROJ", "réservation salle")).isEqualTo("project = PROJ AND text ~ \"réservation salle\" ORDER BY updated DESC");
    }

    @Test
    void caracteresLuceneRetiresEtGuillemetsEchappes() {
        assertThat(JqlBuilder.textSearch("PROJ", "a+b-c&d|e!f(g)h{i}j[k]l^m~n*o?p\\q:r"))
                .isEqualTo("project = PROJ AND text ~ \"a b c d e f g h i j k l m n o p q r\" ORDER BY updated DESC");
        assertThat(JqlBuilder.textSearch("PROJ", "dit \"oui\"")).isEqualTo("project = PROJ AND text ~ \"dit \\\"oui\\\"\" ORDER BY updated DESC");
    }

    @Test
    void injectionEnfermeeDansUnSeulLitteral() {
        String jql = JqlBuilder.textSearch("PROJ", "\") OR project != X OR text ~ (\"");
        assertThat(jql).isEqualTo("project = PROJ AND text ~ \"\\\" OR project = X OR text \\\"\" ORDER BY updated DESC");
        // un seul littéral : hors des guillemets échappés, il ne reste que la structure attendue
        String outside = jql.replace("\\\"", "").replaceAll("\"[^\"]*\"", "\"\"");
        assertThat(outside).isEqualTo("project = PROJ AND text ~ \"\" ORDER BY updated DESC");
    }

    @Test
    void espacesNormalisesEtSaisieVideRefusee() {
        assertThat(JqlBuilder.textSearch("PROJ", "  a \n\t b  ")).endsWith("~ \"a b\" ORDER BY updated DESC");
        assertThatThrownBy(() -> JqlBuilder.textSearch("PROJ", "(*)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JqlBuilder.textSearch("PROJ OR 1=1", "a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ticketsOuvertsOuModifiesDepuisLeRepere() {
        assertThat(JqlBuilder.openOrUpdatedSince("FACT", java.time.Instant.parse("2026-11-05T10:15:00Z")))
                .isEqualTo("project = FACT AND (statusCategory != Done OR updated >= \"2026/11/04 10:15\")"
                        + " ORDER BY updated ASC");
        assertThatThrownBy(() -> JqlBuilder.openOrUpdatedSince("FACT", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> JqlBuilder.openOrUpdatedSince("FACT OR 1=1", java.time.Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ticketsParCle() {
        assertThat(JqlBuilder.keys("FACT", java.util.List.of("FACT-1", "FACT-20")))
                .isEqualTo("project = FACT AND key in (FACT-1, FACT-20) ORDER BY key ASC");
        assertThatThrownBy(() -> JqlBuilder.keys("FACT", java.util.List.of("FACT-1) OR (1=1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JqlBuilder.keys("FACT", java.util.List.of("fact-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JqlBuilder.keys("FACT", java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
        java.util.List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 51).mapToObj(i -> "FACT-" + i).toList();
        assertThatThrownBy(() -> JqlBuilder.keys("FACT", tooMany)).isInstanceOf(IllegalArgumentException.class);
    }
}
