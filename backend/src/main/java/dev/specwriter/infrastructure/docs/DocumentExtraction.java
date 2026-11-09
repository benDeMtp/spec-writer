package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentFolder.Limits;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Extraction du texte d'un fichier Word, Excel, PDF ou HTML (006), sur un fil virtuel et avec un
 * délai : partagée par le dossier synchronisé et les documents joints (009).
 */
final class DocumentExtraction {

    /** Texte extrait, ou la raison du refus. */
    record Result(Extracted document, UnreadReason failure) {
    }

    private DocumentExtraction() {
    }

    static Result extract(Path file, String format, Limits limits) {
        CompletableFuture<Extracted> extraction = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().name("document").start(() -> {
            try {
                extraction.complete(read(file, format, limits.maxChars()));
            } catch (ExtractionException | RuntimeException e) {
                extraction.completeExceptionally(e);
            }
        });
        try {
            return new Result(extraction.get(limits.fileTimeout().toNanos(), TimeUnit.NANOSECONDS), null);
        } catch (TimeoutException e) {
            worker.interrupt();
            return new Result(null, UnreadReason.UNAVAILABLE);
        } catch (ExecutionException e) {
            return new Result(null, e.getCause() instanceof ExtractionException x ? x.reason() : UnreadReason.UNREADABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.interrupt();
            return new Result(null, UnreadReason.UNAVAILABLE);
        }
    }

    private static Extracted read(Path file, String format, int maxChars) throws ExtractionException {
        return switch (format) {
            case "docx" -> OoxmlText.docx(file, maxChars);
            case "xlsx" -> OoxmlText.xlsx(file, maxChars);
            case "pdf" -> PdfText.read(file, maxChars);
            default -> HtmlText.read(file, maxChars);
        };
    }
}
