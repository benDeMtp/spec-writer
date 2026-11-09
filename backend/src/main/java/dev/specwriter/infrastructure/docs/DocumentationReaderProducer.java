package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.infrastructure.platform.ProxySettings;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Lecteur de documentation Antora, avec le proxy d'entreprise et les bornes de FR-013e. */
public class DocumentationReaderProducer {

    @Produces
    @Singleton
    DocumentationReader documentationReader(ProxySettings proxy, ClockPort clock) {
        return new AntoraReader(proxy, clock, AntoraReader.Limits.DEFAULT);
    }

    /** Dossier de documents (006), aux limites de research.md R7. */
    @Produces
    @Singleton
    DocumentFolder documentFolder() {
        return new LocalDocumentFolder(DocumentFolder.Limits.DEFAULT);
    }

    /** Documents joints (009) : mêmes lecteurs et mêmes bornes, fichier temporaire du dossier de données. */
    @Produces
    @Singleton
    AttachmentReader attachmentReader(@ConfigProperty(name = "specwriter.data-dir.resolved") String dataDir) {
        return new TempFileAttachmentReader(Path.of(dataDir, "tmp"), DocumentFolder.Limits.DEFAULT);
    }
}
