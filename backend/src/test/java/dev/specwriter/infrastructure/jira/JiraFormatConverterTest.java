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
                """);
        assertThat(md).contains("# Titre").contains("### Petit").contains("**gras**").contains("`code`").contains("*it*")
                .contains("- un").contains("  - deux").contains("1. premier")
                .contains("```java\nint *x* = 1;\n```");
    }

    @Test
    void blocInconnuRenduEnCodeBrut() {
        String md = converter.toMarkdown("""
                Avant
                {panel:title=Note}
                Contenu *libre*
                {panel}
                ||A||B||
                |1|2|
                Après""");
        assertThat(md).contains("```\n{panel:title=Note}\nContenu *libre*\n{panel}\n```")
                .contains("```\n||A||B||\n|1|2|\n```").contains("Avant").contains("Après");
        assertThat(converter.toMarkdown(null)).isEmpty();
    }
}
