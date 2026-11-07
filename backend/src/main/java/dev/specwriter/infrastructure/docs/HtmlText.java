package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.io.IOException;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Texte d'une page HTML d'un dossier de documents (006) : tout le corps, structuré par
 * {@link StructuredText} (scripts, styles et navigation retirés). Aucune règle Antora.
 */
final class HtmlText {

    private HtmlText() {
    }

    static Extracted read(Path file, int maxChars) throws ExtractionException {
        Document doc;
        try {
            doc = Jsoup.parse(file.toFile(), null);
        } catch (IOException e) {
            throw new ExtractionException(UnreadReason.UNREADABLE);
        }
        String text = StructuredText.of(doc.body());
        if (text.isBlank()) {
            throw new ExtractionException(UnreadReason.NO_TEXT);
        }
        if (text.length() > maxChars) {
            throw new ExtractionException(UnreadReason.TOO_LARGE);
        }
        Element h1 = doc.body().selectFirst("h1");
        String title = !doc.title().isBlank() ? doc.title().strip()
                : h1 != null && !h1.text().isBlank() ? h1.text().strip() : PdfText.baseName(file);
        return new Extracted(title, text);
    }
}
