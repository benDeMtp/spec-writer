package fr.kawamind.infrastructure.docs;

import fr.kawamind.domain.port.AttachmentReader;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.DocumentFolder;
import fr.kawamind.domain.port.SourceTree;
import fr.kawamind.infrastructure.sources.LocalSourceTree;
import fr.kawamind.domain.port.DocumentationReader;
import fr.kawamind.infrastructure.platform.ProxySettings;
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

    /** Sources du code (013), lues à chaque analyse. */
    @Produces
    @Singleton
    SourceTree sourceTree() {
        return new LocalSourceTree();
    }

    /** Documents joints (009) : mêmes lecteurs et mêmes bornes, fichier temporaire du dossier de données. */
    @Produces
    @Singleton
    AttachmentReader attachmentReader(@ConfigProperty(name = "specwriter.data-dir.resolved") String dataDir) {
        return new TempFileAttachmentReader(Path.of(dataDir, "tmp"), DocumentFolder.Limits.DEFAULT);
    }
}
