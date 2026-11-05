package dev.specwriter.infrastructure.architecture;

import dev.specwriter.domain.port.ArchitectureReader;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Lecteur du workspace Structurizr d'un projet (research.md R21). */
public class ArchitectureReaderProducer {

    @Produces
    @Singleton
    ArchitectureReader architectureReader() {
        return new StructurizrReader();
    }
}
