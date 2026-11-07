package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Texte d'un PDF (006, research.md R1), une section par page ; au plus {@link #MAX_PAGES} pages.
 * Un PDF chiffré est signalé protégé, un PDF sans texte (scanné) signalé sans texte.
 */
final class PdfText {

    static final int MAX_PAGES = 300;

    private PdfText() {
    }

    static Extracted read(Path file, int maxChars) throws ExtractionException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (doc.getNumberOfPages() > MAX_PAGES) {
                throw new ExtractionException(UnreadReason.TOO_LARGE);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder out = new StringBuilder();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc).strip().replaceAll("\\r\\n?", "\n");
                if (text.isEmpty()) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append("\n\n");
                }
                out.append("## Page ").append(page).append("\n\n").append(text);
                if (out.length() > maxChars) {
                    throw new ExtractionException(UnreadReason.TOO_LARGE);
                }
            }
            if (out.isEmpty()) {
                throw new ExtractionException(UnreadReason.NO_TEXT);
            }
            String title = doc.getDocumentInformation().getTitle();
            return new Extracted(title == null || title.isBlank() ? baseName(file) : title.strip(), out.toString());
        } catch (InvalidPasswordException e) {
            throw new ExtractionException(UnreadReason.PROTECTED);
        } catch (IOException e) {
            throw new ExtractionException(UnreadReason.UNREADABLE);
        }
    }

    static String baseName(Path file) {
        String name = LocalDocumentFolder.fileName(file);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
