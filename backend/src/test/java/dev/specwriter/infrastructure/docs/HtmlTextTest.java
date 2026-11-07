package dev.specwriter.infrastructure.docs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 006 : page HTML d'un dossier de documents, sans règle Antora. */
class HtmlTextTest {

    @TempDir
    Path tmp;

    @Test
    void titresParagraphesSansScript() throws Exception {
        Path file = Files.writeString(tmp.resolve("Accueil.html"), """
                <html><head><title>Portail</title><script>alert(1)</script><style>p{}</style></head>
                <body><nav>Menu</nav><h1>Accueil</h1><p>Bienvenue sur Aurora.</p>
                <h2>Contacts</h2><table><tr><td>Relai</td><td>équipe</td></tr></table></body></html>""");

        Extracted doc = HtmlText.read(file, 200_000);

        assertThat(doc.title()).isEqualTo("Portail");
        assertThat(doc.text()).isEqualTo("""
                # Accueil

                Bienvenue sur Aurora.

                ## Contacts

                | Relai | équipe |""");
    }

    @Test
    void titreParDefautEtPageVide() throws Exception {
        Path h1 = Files.writeString(tmp.resolve("a.html"), "<html><body><h1>Guide</h1><p>Texte.</p></body></html>");
        assertThat(HtmlText.read(h1, 200_000).title()).isEqualTo("Guide");
        Path bare = Files.writeString(tmp.resolve("Notes.htm"), "<p>Texte.</p>");
        assertThat(HtmlText.read(bare, 200_000).title()).isEqualTo("Notes");
        Path empty = Files.writeString(tmp.resolve("vide.html"), "<html><body><script>x</script></body></html>");
        OoxmlTextTest.failsWith(() -> HtmlText.read(empty, 200_000), UnreadReason.NO_TEXT);
        Path big = Files.writeString(tmp.resolve("gros.html"), "<p>" + "x".repeat(300) + "</p>");
        OoxmlTextTest.failsWith(() -> HtmlText.read(big, 100), UnreadReason.TOO_LARGE);
    }
}
