package dev.specwriter.infrastructure.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** T097 : la saisie reste un littéral JQL (OWASP A03). */
class JqlBuilderTest {

    @Test
    void requeteDeBase() {
        assertThat(JqlBuilder.textSearch("PROJ", "réservation salle")).isEqualTo("project = PROJ AND text ~ \"réservation salle\"");
    }

    @Test
    void caracteresLuceneRetiresEtGuillemetsEchappes() {
        assertThat(JqlBuilder.textSearch("PROJ", "a+b-c&d|e!f(g)h{i}j[k]l^m~n*o?p\\q:r"))
                .isEqualTo("project = PROJ AND text ~ \"a b c d e f g h i j k l m n o p q r\"");
        assertThat(JqlBuilder.textSearch("PROJ", "dit \"oui\"")).isEqualTo("project = PROJ AND text ~ \"dit \\\"oui\\\"\"");
    }

    @Test
    void injectionEnfermeeDansUnSeulLitteral() {
        String jql = JqlBuilder.textSearch("PROJ", "\") OR project != X OR text ~ (\"");
        assertThat(jql).isEqualTo("project = PROJ AND text ~ \"\\\" OR project = X OR text \\\"\"");
        // un seul littéral : hors des guillemets échappés, il ne reste que la structure attendue
        String outside = jql.replace("\\\"", "").replaceAll("\"[^\"]*\"", "\"\"");
        assertThat(outside).isEqualTo("project = PROJ AND text ~ \"\"");
    }

    @Test
    void espacesNormalisesEtSaisieVideRefusee() {
        assertThat(JqlBuilder.textSearch("PROJ", "  a \n\t b  ")).endsWith("~ \"a b\"");
        assertThatThrownBy(() -> JqlBuilder.textSearch("PROJ", "(*)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JqlBuilder.textSearch("PROJ OR 1=1", "a")).isInstanceOf(IllegalArgumentException.class);
    }
}
