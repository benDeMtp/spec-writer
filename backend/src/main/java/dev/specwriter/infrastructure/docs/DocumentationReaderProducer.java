package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.infrastructure.platform.ProxySettings;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Lecteur de documentation Antora, avec le proxy d'entreprise et les bornes de FR-013e. */
public class DocumentationReaderProducer {

    @Produces
    @Singleton
    DocumentationReader documentationReader(ProxySettings proxy, ClockPort clock) {
        return new AntoraReader(proxy, clock, AntoraReader.Limits.DEFAULT);
    }
}
