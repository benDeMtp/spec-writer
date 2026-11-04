package dev.specwriter.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.KnowledgeIndex.Hit;
import dev.specwriter.domain.port.KnowledgeIndex.Key;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Index local de la connaissance (T093, research.md R18). */
@QuarkusTest
class SqliteKnowledgeIndexIT {

    static final Instant T0 = Instant.parse("2026-11-04T09:00:00Z");
    static final String MODEL = "test-model@1";
    static final int DIMENSIONS = 384;

    @Inject
    KnowledgeIndex index;
    @Inject
    ProjectRepository projects;
    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;

    UUID project;

    @BeforeEach
    void projet() {
        project = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Connaissance " + project.toString().substring(0, 8)),
                null, null, T0));
    }

    @AfterEach
    void nettoyer() {
        projects.delete(project);
    }

    static float[] vector(long seed) {
        Random random = new Random(seed);
        float[] v = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] = (float) random.nextGaussian();
            norm += v[i] * v[i];
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] /= (float) Math.sqrt(norm);
        }
        return v;
    }

    KnowledgeItem section(String ref, String title, String body) {
        return KnowledgeItem.docSection(project, ref, 0, title, body, null, T0, T0);
    }

    KnowledgeItem ticket(String key, String title, String body) {
        return KnowledgeItem.ticket(project, key, title, body, "À faire", "new", "Story", T0, T0);
    }

    @Test
    void ecritureIdempotenteParEmpreinte() {
        KnowledgeItem item = section("http://doc/a.html#_avoir", "Avoir", "Annule une facture.");
        index.save(item, vector(1), MODEL);
        index.save(item, vector(1), MODEL);
        assertThat(index.count(project, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(index.hashes(project, KnowledgeKind.DOC_SECTION)).containsEntry(Key.of(item), item.contentHash());

        KnowledgeItem changed = section("http://doc/a.html#_avoir", "Avoir", "Annule tout ou partie d'une facture.");
        index.save(changed, vector(2), MODEL);
        assertThat(index.count(project, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(index.find(project, Key.of(item))).get().extracting(KnowledgeItem::body)
                .isEqualTo("Annule tout ou partie d'une facture.");
    }

    @Test
    void recherchePleinTexteSansSyntaxeNiAccents() {
        index.save(section("u#1", "Règles des avoirs", "Le plafond est le restant dû."), null, null);
        index.save(section("u#2", "Glossaire", "Une écriture comptable par facture."), null, null);
        assertThat(index.search(project, "regle avoir", null, MODEL, List.of(), 10)).extracting(h -> h.item().ref())
                .containsExactly("u#1");
        assertThat(index.search(project, "ecriture", null, MODEL, List.of(), 10)).extracting(h -> h.item().ref())
                .containsExactly("u#2");
        // opérateurs FTS5 et guillemets restent des littéraux ; un mot suffit (besoin de plusieurs phrases)
        assertThat(index.search(project, "\"avoir* NEAR( AND", null, MODEL, List.of(), 10)).extracting(h -> h.item().ref())
                .containsExactly("u#1");
        assertThat(index.search(project, "plafond inconnu introuvable", null, MODEL, List.of(), 10))
                .extracting(h -> h.item().ref()).containsExactly("u#1");
        assertThat(index.search(project, "  ", null, MODEL, List.of(), 10)).isEmpty();
    }

    @Test
    void rechercheParVecteurs() {
        index.save(section("u#1", "Premier", "aaa"), vector(10), MODEL);
        index.save(section("u#2", "Second", "bbb"), vector(20), MODEL);
        index.save(section("u#3", "Troisième", "ccc"), vector(30), MODEL);
        List<Hit> hits = index.search(project, "aucun terme commun", vector(20), MODEL, List.of(), 10);
        assertThat(hits).first().extracting(h -> h.item().ref()).isEqualTo("u#2");
    }

    @Test
    void fusionDesRangsFavoriseCeQueLesDeuxRecherchesTrouvent() {
        index.save(section("u#mots", "Avoir partiel", "Avoir sur une ligne."), vector(1), MODEL);
        index.save(section("u#sens", "Remboursement", "Rendre une partie du montant."), vector(2), MODEL);
        index.save(section("u#deux", "Avoir", "Avoir et remboursement."), vector(3), MODEL);
        float[] query = vector(3);
        List<Hit> hits = index.search(project, "avoir", query, MODEL, List.of(), 10);
        assertThat(hits).first().extracting(h -> h.item().ref()).isEqualTo("u#deux");
        assertThat(hits).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
    }

    @Test
    void filtreParSorte() {
        index.save(section("u#1", "Avoir", "Section sur l'avoir."), null, null);
        index.save(ticket("FACT-9", "Émettre un avoir partiel", "Avoir sur une ligne."), null, null);
        assertThat(index.search(project, "avoir", null, MODEL, EnumSet.of(KnowledgeKind.TICKET), 10))
                .extracting(h -> h.item().ref()).containsExactly("FACT-9");
    }

    @Test
    void lesProjetsNeSeMelangentPas() {
        index.save(section("u#1", "Avoir", "Section sur l'avoir."), vector(1), MODEL);
        UUID other = UUID.randomUUID();
        projects.save(Project.create(other, ProjectName.of("Autre " + other.toString().substring(0, 8)), null, null, T0));
        try {
            assertThat(index.search(other, "avoir", vector(1), MODEL, List.of(), 10)).isEmpty();
        } finally {
            projects.delete(other);
        }
    }

    @Test
    void suppressionDesElementsDisparus() {
        KnowledgeItem kept = ticket("FACT-7", "Annuler une facture", "Par un avoir.");
        KnowledgeItem gone = ticket("FACT-8", "Supprimé", "Disparu de Jira.");
        index.save(kept, null, null);
        index.save(gone, null, null);
        assertThat(index.retain(project, KnowledgeKind.TICKET, Set.of(Key.of(kept)))).isEqualTo(1);
        assertThat(index.find(project, Key.of(gone))).isEmpty();
        assertThat(index.search(project, "disparu", null, MODEL, List.of(), 10)).isEmpty();
        index.delete(project, Key.of(kept));
        assertThat(index.count(project, KnowledgeKind.TICKET)).isZero();
    }

    @Test
    void elementsSansVecteurPuisRevectorises() {
        KnowledgeItem a = section("u#1", "Avoir", "a");
        KnowledgeItem b = section("u#2", "Facture", "b");
        index.save(a, null, null);
        index.save(b, vector(2), "ancien-modele");
        assertThat(index.withoutVector(project, MODEL, 10)).extracting(KnowledgeItem::ref)
                .containsExactlyInAnyOrder("u#1", "u#2");
        index.saveVector(project, Key.of(a), vector(1), MODEL);
        index.saveVector(project, Key.of(b), vector(2), MODEL);
        assertThat(index.withoutVector(project, MODEL, 10)).isEmpty();
        assertThat(index.search(project, "zzz", vector(1), MODEL, List.of(), 1)).extracting(h -> h.item().ref())
                .containsExactly("u#1");
    }

    @Test
    void champsDesSortesConserves() {
        KnowledgeItem adr = KnowledgeItem.adr(project, "http://doc/adr-002.html", 0, "ADR-002", "Synchrone.",
                AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-005"), T0, T0);
        KnowledgeItem glossary = KnowledgeItem.docSection(project, "http://doc/glossaire.html#_avoir", 0, "Avoir",
                "Document qui annule.", KnowledgeRole.VOCABULARY, T0, T0);
        KnowledgeItem element = KnowledgeItem.archElement(project, new ArchElement("Suez/avoirs-emis",
                ArchElement.Type.CONTAINER, ArchScope.PROJECT, true, "Avoirs émis", "Kafka topic"), T0, T0);
        KnowledgeItem closed = KnowledgeItem.ticket(project, "FACT-7", "Annuler", "x", "Terminé", "done", "Story", T0, T0);
        List.of(adr, glossary, element, closed).forEach(i -> index.save(i, null, null));

        assertThat(index.find(project, Key.of(adr))).get().satisfies(i -> {
            assertThat(i.adrStatus()).isEqualTo(AdrStatus.SUPERSEDED);
            assertThat(i.supersededBy()).isEqualTo("ADR-005");
        });
        assertThat(index.find(project, Key.of(element))).get().satisfies(i -> {
            assertThat(i.scope()).isEqualTo(ArchScope.PROJECT);
            assertThat(i.transport()).isTrue();
            assertThat(i.elementType()).isEqualTo("CONTAINER");
        });
        assertThat(index.find(project, Key.of(closed))).get().extracting(KnowledgeItem::closed).isEqualTo(true);
        assertThat(index.withRole(project)).extracting(KnowledgeItem::ref).containsExactly(glossary.ref());
    }

    @Test
    void modeleDArchitectureRemplaceEtRelu() {
        index.save(KnowledgeItem.archElement(project, new ArchElement("Facturation/API", ArchElement.Type.CONTAINER,
                ArchScope.PROJECT, false, null, null), T0, T0), null, null);
        index.save(KnowledgeItem.archElement(project, new ArchElement("Suez/topic", ArchElement.Type.CONTAINER,
                ArchScope.COMMON, true, null, null), T0, T0), null, null);
        ArchitectureModel model = new ArchitectureModel(List.of(),
                List.of(new ArchRelation("Facturation/API", "Suez/topic", "Publie", "Kafka")),
                List.of(new ArchDeployment("Facturation/API", "Production", "Azure", "Azure/AKS", 2, false)));
        index.replaceArchitecture(project, model);
        index.replaceArchitecture(project, model);

        ArchitectureModel read = index.architecture(project);
        assertThat(read.relations()).containsExactlyElementsOf(model.relations());
        assertThat(read.deployments()).containsExactlyElementsOf(model.deployments());
        assertThat(read.elements()).extracting(ArchElement::name, ArchElement::scope, ArchElement::transport)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Facturation/API", ArchScope.PROJECT, false),
                        org.assertj.core.groups.Tuple.tuple("Suez/topic", ArchScope.COMMON, true));
    }

    @Test
    void suppressionEnCascadeAvecLeProjet() {
        UUID other = UUID.randomUUID();
        projects.save(Project.create(other, ProjectName.of("Cascade " + other.toString().substring(0, 8)), null, null, T0));
        index.save(KnowledgeItem.docSection(other, "u#1", 0, "Cascade", "corps", null, T0, T0), vector(1), MODEL);
        index.replaceArchitecture(other, new ArchitectureModel(List.of(), List.of(new ArchRelation("A", "B", null, null)),
                List.of(new ArchDeployment("A", "Production", "Azure", "Azure", 1, false))));
        projects.delete(other);
        assertThat(index.count(other, KnowledgeKind.DOC_SECTION)).isZero();
        assertThat(index.search(other, "cascade", vector(1), MODEL, List.of(), 10)).isEmpty();
        assertThat(index.architecture(other).relations()).isEmpty();
        assertThat(index.architecture(other).deployments()).isEmpty();
    }

    @Test
    void rechercheSurDixMilleElementsEnMoinsDe50Ms() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 10_000; i++) {
                index.save(section("u#" + i, "Section " + i, "Texte numéro " + i + " sur la facturation."), vector(i),
                        MODEL);
            }
        });
        float[] query = vector(4_242);
        for (int i = 0; i < 5; i++) {
            index.search(project, "facturation 4242", query, MODEL, List.of(), 20);
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            List<Hit> hits = index.search(project, "facturation 4242", query, MODEL, List.of(), 20);
            best = Math.min(best, System.nanoTime() - start);
            assertThat(hits).first().extracting(h -> h.item().ref()).isEqualTo("u#4242");
        }
        assertThat(best / 1_000_000).isLessThan(50);
    }

    @Test
    void aucunContenuJournalise() throws IOException {
        String marker = "contenu-secret-" + UUID.randomUUID();
        index.save(section("u#secret", marker, marker), vector(1), MODEL);
        index.search(project, marker, vector(1), MODEL, List.of(), 10);
        Path journal = Path.of(dataDir, "logs", "journal.jsonl");
        if (Files.exists(journal)) {
            assertThat(Files.readString(journal)).doesNotContain(marker);
        }
    }
}
