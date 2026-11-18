package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;

import fr.kawamind.domain.port.AgentAccessRepository;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.TaskRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Lectures appelées hors d'une requête HTTP, comme le font les tours de la conversation et la
 * proposition d'une nouvelle version (exécuteur) : sans transaction, Hibernate lève
 * ContextNotActiveException (« Proposer une nouvelle version » échouait sur un projet avec des
 * documents OneDrive ou des pages Confluence, relus par documentParts).
 */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class BackgroundReadsIT {

    @Inject
    KnowledgeIndex index;

    @Inject
    TaskRepository tasks;

    @Inject
    SessionRepository sessions;

    @Inject
    AgentAccessRepository agents;

    @Test
    void lesLecturesFonctionnentHorsDUneRequete() throws Exception {
        UUID project = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        List<Runnable> reads = List.of(
                () -> index.documentParts(project, "docs:" + UUID.randomUUID() + "/Demande.docx"),
                () -> index.mentionables(project),
                () -> tasks.tasks(session),
                () -> tasks.proposal(session),
                () -> tasks.tracking(session),
                () -> sessions.implementation(session),
                agents::enabled,
                () -> agents.page(0, 10),
                agents::count);
        var pool = Executors.newSingleThreadExecutor();
        try {
            for (Runnable read : reads) {
                assertThatCode(() -> CompletableFuture.runAsync(read, pool).get()).doesNotThrowAnyException();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
