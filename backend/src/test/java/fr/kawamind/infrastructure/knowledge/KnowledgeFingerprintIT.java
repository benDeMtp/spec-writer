package fr.kawamind.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, US6 (R8) : empreinte d'une source citée, lue dans l'index SQLite. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class KnowledgeFingerprintIT {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");
    static final Set<KnowledgeKind> DOCS = Set.of(KnowledgeKind.DOC_SECTION, KnowledgeKind.ADR);

    @Inject
    KnowledgeIndex index;

    @Inject
    ProjectRepository projects;

    @Test
    void stablePourLesMemesExtraitsChangeAvecUnExtraitVidePourUneReferenceAbsente() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Empreintes " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        try {
            String page = "https://wiki/pages/42";
            index.save(KnowledgeItem.docSection(p.id(), page, 0, "Règles", "Un avoir est plafonné.", null, T0, T0), null, "m");
            index.save(KnowledgeItem.docSection(p.id(), page + "#plafonds", 0, "Plafonds", "500 euros.", null, T0, T0), null,
                    "m");
            index.save(KnowledgeItem.docSection(p.id(), "https://wiki/pages/420", 0, "Autre", "Sans rapport.", null, T0, T0),
                    null, "m");
            String first = index.fingerprint(p.id(), DOCS, page).orElseThrow();
            assertThat(index.fingerprint(p.id(), DOCS, page)).contains(first);

            index.save(KnowledgeItem.docSection(p.id(), page + "#plafonds", 0, "Plafonds", "1 000 euros.", null, T0, T0),
                    null, "m");
            assertThat(index.fingerprint(p.id(), DOCS, page)).hasValueSatisfying(v -> assertThat(v).isNotEqualTo(first));
            assertThat(index.fingerprint(p.id(), DOCS, "https://wiki/pages/99")).isEmpty();
            assertThat(index.fingerprint(p.id(), Set.of(KnowledgeKind.TICKET), page)).isEmpty();
        } finally {
            projects.delete(p.id());
        }
    }
}
