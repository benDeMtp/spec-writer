package dev.specwriter.infrastructure.jira;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T099 : Markdown vers wiki markup, et retour pour l'affichage. */
class JiraFormatConverterTest {

    final JiraFormatConverter converter = new JiraFormatConverter();

    @Test
    void markdownVersWiki() {
        String wiki = converter.toWiki("""
                # Titre

                ## Sous titre

                Texte **gras**, *italique*, `code` et ~~barré~~.

                - un
                - deux
                  - imbriqué

                1. premier
                2. second

                | A | B |
                |---|---|
                | 1 | 2 |

                ```java
                int x = 1;
                ```
                """);
        assertThat(wiki).contains("h1. Titre").contains("h2. Sous titre")
                .contains("*gras*").contains("_italique_").contains("{{code}}").contains("-barré-")
                .contains("* un").contains("** imbriqué").contains("# premier")
                .contains("||A||B||").contains("|1|2|")
                .contains("{code:java}").contains("int x = 1;");
    }

    @Test
    void caracteresSpeciauxDuWikiEchappes() {
        String wiki = converter.toWiki("prix {total} [lien] a|b x^2 ~env c+d et_e");
        assertThat(wiki).contains("\\{total\\}").contains("\\[lien\\]").contains("a\\|b").contains("x\\^2")
                .contains("\\~env").contains("c\\+d").contains("et\\_e").doesNotContain("{total}");
    }

    @Test
    void wikiVersMarkdown() {
        String md = converter.toMarkdown("""
                h1. Titre
                h3. Petit
                Texte *gras* et {{code}} et _it_.
                * un
                ** deux
                # premier
                {code:java}
                int *x* = 1;
                {code}
                """, URL);
        assertThat(md).contains("# Titre").contains("### Petit").contains("**gras**").contains("`code`").contains("*it*")
                .contains("- un").contains("  - deux").contains("1. premier")
                .contains("```java\nint *x* = 1;\n```");
    }

    @Test
    void blocInconnuRenduEnCodeBrut() {
        String md = converter.toMarkdown("""
                Avant
                {expand:Détails}
                Contenu *libre*
                {expand}
                Après""", URL);
        assertThat(md).contains("```\n{expand:Détails}\nContenu *libre*\n{expand}\n```").contains("Avant").contains("Après");
        assertThat(converter.toMarkdown(null, URL)).isEmpty();
    }

    // --- T152 : syntaxe Jira interprétée à l'affichage (FR-043a) ---

    static final String URL = "https://jira.example/browse/PROJ-1";

    @Test
    void tableauAvecLigneDEnTete() {
        String md = converter.toMarkdown("""
                Avant
                ||Acteur||Action||
                |Salarié|*Réserve* une place|
                |Gardien|Valide|
                Après""", URL);
        assertThat(md).contains("""
                | Acteur | Action |
                | --- | --- |
                | Salarié | **Réserve** une place |
                | Gardien | Valide |
                """).contains("Avant\n\n|").contains("|\n\nAprès");
    }

    @Test
    void tableauSansEnTeteEtEnTeteEnPremiereColonne() {
        String md = converter.toMarkdown("""
                ||Clé|PROJ-1|
                ||État|Ouvert|""", URL);
        assertThat(md).contains("""
                |  |  |
                | --- | --- |
                | **Clé** | PROJ-1 |
                | **État** | Ouvert |
                """);
    }

    @Test
    void celluleAvecRetourALaLigneTuyauEchappeEtLien() {
        String md = converter.toMarkdown("""
                ||Cas||Détail||
                |Un\\\\deux|a\\|b|
                |Lien|[doc|https://doc.example/a_b_c] et [~jdupont]|""", URL);
        assertThat(md).contains("| Un<br>deux | a\\|b |")
                .contains("| Lien | [doc](https://doc.example/a_b_c) et [mention : jdupont](" + URL + ") |");
    }

    @Test
    void celluleSurPlusieursLignes() {
        String md = converter.toMarkdown("""
                ||A||B||
                |début
                suite|2|""", URL);
        assertThat(md).contains("| début<br>suite | 2 |");
    }

    @Test
    void liensSursSeulementPourHttpHttpsEtMailto() {
        String md = converter.toMarkdown(
                "[Site|https://ex.example/x_y] [http://ex.example] [écrire|mailto:a@ex.example] "
                        + "[piège|javascript:alert(1)] [local|file:///etc/passwd] [#ancre]", URL);
        assertThat(md).contains("[Site](https://ex.example/x_y)").contains("<http://ex.example>")
                .contains("[écrire](mailto:a@ex.example)")
                .doesNotContain("](javascript:").doesNotContain("](file:")
                .contains("piège (javascript:alert(1))").contains("local (file:///etc/passwd)").contains("#ancre");
    }

    @Test
    void citationsPanneauxCouleurEtMisesEnForme() {
        String md = converter.toMarkdown("""
                bq. Une citation *forte*
                {quote}
                Ligne un
                Ligne deux
                {quote}
                {panel:title=À noter|borderStyle=dashed}
                Contenu du _panneau_
                {panel}
                {color:red}Texte rouge{color} puis -barré- et +souligné+ et {{a*b*c}}
                porte-monnaie, c++ et 10 - 5
                ----""", URL);
        assertThat(md).contains("> Une citation **forte**")
                .contains("> Ligne un\n> Ligne deux")
                .contains("> **À noter**\n>\n> Contenu du *panneau*")
                .contains("Texte rouge puis ~~barré~~ et <u>souligné</u> et `a*b*c`")
                .contains("porte-monnaie, c++ et 10 - 5")
                .contains("\n---");
        assertThat(md).doesNotContain("{color").doesNotContain("{panel").doesNotContain("{quote");
    }

    @Test
    void imagesPiecesJointesEtMentionsRemplaceesParUnRenvoiVersLeTicket() {
        String md = converter.toMarkdown("Voir !schema.png! et !capture.png|thumbnail! puis [^cahier.pdf] et [~jdupont]", URL);
        assertThat(md).contains("[image : schema.png](" + URL + ")").contains("[image : capture.png](" + URL + ")")
                .contains("[pièce jointe : cahier.pdf](" + URL + ")").contains("[mention : jdupont](" + URL + ")")
                .doesNotContain("![");
        assertThat(converter.toMarkdown("!schema.png!", null)).isEqualTo("image : schema.png\n");
    }

    @Test
    void allerRetourDUnTableau() {
        String table = """
                | Acteur | Action |
                | --- | --- |
                | Salarié | Réserve |
                """;
        assertThat(converter.toMarkdown(converter.toWiki(table), URL)).contains(table);
    }
}
