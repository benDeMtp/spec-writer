package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.knowledge.AdrList.AdrSummary;
import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.FakeDocumentFiles;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 004, US2 : liste des ADR du projet (FR-003 à FR-009). */
class ListAdrsTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-11-05T09:00:00Z");
    static final String FOLDER = "/doc/aurora";

    final JiraWorld w = new JiraWorld();
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final FakeDocumentFiles files = new FakeDocumentFiles();
    Indexations indexations;
    ListAdrs list;

    void adr(String ref, int chunk, String title, AdrStatus status, String supersededBy) {
        index.save(KnowledgeItem.adr(P, ref, chunk, title, "corps " + chunk,
                status == null ? AdrStatus.Statement.UNKNOWN : AdrStatus.Statement.of(status, supersededBy), T, T),
                null, null);
    }

    @BeforeEach
    void setUp() {
        w.docSources.save(new DocSource(UUID.randomUUID(), P, DocSource.Kind.FOLDER, FOLDER, null, null));
        indexations = new Indexations(null, w.projects, task -> { }, w.clock, false);
        list = new ListAdrs(w.projects, index, w.docSources, files, indexations);
        adr("adr/0017-bff.html", 0, "17. BFF composé", AdrStatus.PROPOSED, null);
        adr("adr/0017-bff.html", 1, "17. BFF composé", AdrStatus.PROPOSED, null);
        adr("adr/0002-schema.html", 0, "2. Un seul schéma", AdrStatus.SUPERSEDED, "ADR-0005");
        adr("adr/0005-domaines.html", 0, "5. Trois domaines", AdrStatus.ACCEPTED, null);
        adr("adr/0005-contextes.html", 0, "5. Bounded contexts", AdrStatus.ACCEPTED, null);
        adr("adr/0009-cache.html", 0, "9. Cache", AdrStatus.SUPERSEDED, "ADR-0042");
        adr("adr/0011-vieux.html", 0, "11. Vieux", AdrStatus.DEPRECATED, null);
        adr("adr/0012-flou.html", 0, "12. Flou", null, null);
        adr("decisions/sans-numero.html", 0, "Architecture Decision Record : sans numéro", AdrStatus.PROPOSED, null);
        adr("https://doc.example.com/adr/adr-003.html", 0, "ADR-003 : en ligne", AdrStatus.ACCEPTED, null);
        files.put(FOLDER, "adr/0005-domaines.html", "<html/>");
    }

    /** 006 : un ADR d'un dossier de documents garde son numéro, sans lien (fichiers jamais servis). */
    @Test
    void adrDUnDossierDeDocumentsSansLien() {
        String ref = "docs:" + UUID.randomUUID() + "/Decisions/0018-ihm.docx";
        adr(ref, 0, "IHM nginx", AdrStatus.ACCEPTED, null);
        files.put(FOLDER, ref, "<html/>");
        AdrSummary summary = byTitle(list.run(P)).get("IHM nginx");
        assertThat(summary.number()).isEqualTo(18);
        assertThat(summary.url()).isNull();
    }

    @Test
    void uneLigneParAdrTrieesParNumeroSansNumeroALaFin() {
        AdrList adrs = list.run(P);
        assertThat(adrs.adrs()).extracting(AdrSummary::title).containsExactly("2. Un seul schéma",
                "ADR-003 : en ligne", "5. Bounded contexts", "5. Trois domaines", "9. Cache", "11. Vieux", "12. Flou",
                "17. BFF composé", "Architecture Decision Record : sans numéro");
        assertThat(adrs.adrs().getFirst().number()).isEqualTo(2);
        assertThat(adrs.adrs().getLast().number()).isNull();
    }

    @Test
    void statutsSignalementsEtTransmission() {
        Map<String, AdrSummary> byTitle = byTitle(list.run(P));
        assertThat(byTitle.get("12. Flou").status()).isEqualTo("UNKNOWN");
        assertThat(byTitle.get("5. Trois domaines").duplicate()).isTrue();
        assertThat(byTitle.get("5. Bounded contexts").duplicate()).isTrue();
        assertThat(byTitle.get("17. BFF composé").duplicate()).isFalse();
        assertThat(byTitle.get("2. Un seul schéma")).satisfies(a -> {
            assertThat(a.supersededBy()).isEqualTo("ADR-0005");
            assertThat(a.successorFound()).isTrue();
            assertThat(a.sentToAi()).isFalse();
        });
        assertThat(byTitle.get("9. Cache").successorFound()).isFalse();
        assertThat(byTitle.get("11. Vieux").sentToAi()).isFalse();
        assertThat(byTitle.get("12. Flou").sentToAi()).isTrue();
        assertThat(byTitle.get("17. BFF composé").supersededBy()).isNull();
    }

    @Test
    void resumeParStatutSurTousLesAdr() {
        assertThat(list.run(P).byStatus()).containsExactlyInAnyOrderEntriesOf(Map.of("ACCEPTED", 3, "PROPOSED", 2,
                "SUPERSEDED", 2, "DEPRECATED", 1, "UNKNOWN", 1));
    }

    @Test
    void adresseDuSiteOuDuFichierServiParLOutil() {
        Map<String, AdrSummary> byTitle = byTitle(list.run(P));
        assertThat(byTitle.get("ADR-003 : en ligne").url()).isEqualTo("https://doc.example.com/adr/adr-003.html");
        assertThat(byTitle.get("5. Trois domaines").url())
                .isEqualTo("/api/projects/" + P + "/doc-files/adr/0005-domaines.html");
        // absent des dossiers déclarés : pas de lien
        assertThat(byTitle.get("17. BFF composé").url()).isNull();
    }

    @Test
    void indexationEnCoursSignalee() {
        assertThat(list.run(P).indexing()).isFalse();
        indexations.start(P, Mode.INCREMENTAL);
        assertThat(list.run(P).indexing()).isTrue();
    }

    @Test
    void projetSansAdrEtProjetInconnu() {
        index.items.clear();
        assertThat(list.run(P).adrs()).isEmpty();
        assertThatThrownBy(() -> list.run(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    private static Map<String, AdrSummary> byTitle(AdrList adrs) {
        return adrs.adrs().stream().collect(java.util.stream.Collectors.toMap(AdrSummary::title, a -> a));
    }

    static List<String> titles(AdrList adrs) {
        return adrs.adrs().stream().map(AdrSummary::title).toList();
    }
}
