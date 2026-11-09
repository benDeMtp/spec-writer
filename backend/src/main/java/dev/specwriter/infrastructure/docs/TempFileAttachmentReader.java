package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.attachment.AttachmentFormat;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.DocumentFolder.Limits;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import org.jboss.logging.Logger;

/**
 * Document joint lu par les lecteurs de la 006 (009, research.md R2) : les lecteurs prennent un
 * fichier ; le contenu reçu est écrit dans un fichier temporaire, réservé au propriétaire, supprimé
 * dès la lecture faite. Rien d'autre n'est écrit.
 */
public final class TempFileAttachmentReader implements AttachmentReader {

    private static final Logger LOG = Logger.getLogger(TempFileAttachmentReader.class);

    private final Path directory;
    private final Limits limits;

    public TempFileAttachmentReader(Path directory, Limits limits) {
        this.directory = directory;
        this.limits = limits;
    }

    @Override
    public Reading read(String fileName, byte[] content) {
        AttachmentFormat format = AttachmentFormat.of(fileName).orElse(null);
        if (format == null) {
            return Reading.failed(UnreadReason.UNREADABLE);
        }
        if (content.length > limits.maxFileBytes()) {
            return Reading.failed(UnreadReason.TOO_LARGE);
        }
        String extension = extension(fileName);
        Path file = null;
        try {
            Files.createDirectories(directory);
            file = Files.createTempFile(directory, "joint-", "." + extension);
            restrict(file);
            Files.write(file, content);
            DocumentExtraction.Result result = DocumentExtraction.extract(file, extension, limits);
            if (result.failure() != null) {
                return Reading.failed(result.failure());
            }
            return Reading.of(result.document().title(), result.document().text());
        } catch (IOException e) {
            LOG.warnf("Document joint : fichier temporaire impossible (%s)", e.getClass().getSimpleName());
            return Reading.failed(UnreadReason.UNREADABLE);
        } finally {
            delete(file);
        }
    }

    private static String extension(String fileName) {
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows : le dossier de données est déjà propre à l'utilisateur
            LOG.debug("Document joint : permissions POSIX indisponibles");
        }
    }

    private static void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warnf("Document joint : fichier temporaire non supprimé (%s)", e.getClass().getSimpleName());
        }
    }
}
