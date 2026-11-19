package fr.kawamind.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.knowledge.ArchElement;
import fr.kawamind.domain.knowledge.ArchScope;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.Provenance;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.ReliabilitySettings;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.port.ConfluencePageStates.PageState;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.ExclusionPatterns;
import fr.kawamind.support.InMemoryConfluencePages;
import fr.kawamind.support.InMemoryDocSources;
import fr.kawamind.support.InMemoryReliability;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T007 (016) : provenance d'un élément indexé, table de data-model.md. */
class ProvenanceResolverTest {

    static final UUID P = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID ANTORA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID SHAREPOINT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID ROOT_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID ROOT_B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    static final Instant UPDATED = Instant.parse("2026-03-03T10:00:00Z");
    static final Instant INDEXED = Instant.parse("2026-11-17T10:00:00Z");
    static final String PAGE = "https://wiki/pages/viewpage.action?pageId=42";

    final InMemoryDocSources sources = new InMemoryDocSources();
    final InMemoryReliability reliability = new InMemoryReliability();
    final InMemoryConfluencePages pages = new InMemoryConfluencePages(sources);
    final ProvenanceResolver resolver = new ProvenanceResolver(sources, reliability, pages);

    @BeforeEach
    void setUp() {
        sources.save(new DocSource(ANTORA, P, DocSource.Kind.URL, "https://doc", null, null));
        sources.save(new DocSource(SHAREPOINT, P, DocSource.Kind.DOCUMENTS, "/Users/moi/SharePoint", null, null,
                ExclusionPatterns.NONE));
        sources.save(DocSource.confluence(ROOT_A, P, PAGE, "42", "Remboursements", null));
        sources.save(DocSource.confluence(ROOT_B, P, "https://wiki/pages/viewpage.action?pageId=7", "7", "Frais", null));
    }

    static KnowledgeItem section(String ref, UUID source, Instant updated) {
        KnowledgeItem item = KnowledgeItem.docSection(P, ref, 0, "Plafond", "Le plafond est de 500 euros.", null,
                updated, INDEXED);
        return source == null ? item : item.withSourceId(source);
    }

    @Test
    void origineDeChaqueSorte() {
        ProvenanceResolver.Resolution r = resolver.forProject(P);
        assertThat(r.of(KnowledgeItem.ticket(P, "FACT-7", "Avoir", "", "Ouvert", "new", "Story", UPDATED, INDEXED))
                .origin()).isEqualTo(SourceOrigin.JIRA);
        ArchElement element = new ArchElement("Facturation", ArchElement.Type.CONTAINER, ArchScope.PROJECT, false,
                null, null, null);
        assertThat(r.of(KnowledgeItem.archElement(P, element, null, INDEXED)).origin())
                .isEqualTo(SourceOrigin.ARCHITECTURE);
        assertThat(r.of(section("docs:" + SHAREPOINT + "/Demande.docx#2", null, UPDATED)).origin())
                .isEqualTo(SourceOrigin.DOCUMENTS);
        assertThat(r.of(section("https://doc/billing/avoirs.html#regles", ANTORA, null)).origin())
                .isEqualTo(SourceOrigin.ANTORA);
        assertThat(r.of(section(PAGE + "#plafond", ROOT_A, UPDATED)).origin()).isEqualTo(SourceOrigin.CONFLUENCE);
        // page d'un site lue avant le rattachement à sa source
        assertThat(r.of(section("https://doc/ancien.html", null, null)).origin()).isEqualTo(SourceOrigin.ANTORA);
    }

    @Test
    void dateALaSourceJamaisCelleDeLIndexation() {
        ProvenanceResolver.Resolution r = resolver.forProject(P);
        assertThat(r.of(section(PAGE, ROOT_A, UPDATED)).updatedAt()).isEqualTo(UPDATED);
        Provenance unknown = r.of(section("https://doc/billing/avoirs.html", ANTORA, null));
        assertThat(unknown.updatedAt()).isNull();
        assertThat(unknown.dateTag()).isEqualTo("inconnue");
    }

    @Test
    void niveauxParDefautEtReglages() {
        ProvenanceResolver.Resolution r = resolver.forProject(P);
        assertThat(r.of(section("docs:" + SHAREPOINT + "/Demande.docx#2", null, UPDATED)).level())
                .isEqualTo(Reliability.REFERENCE);
        assertThat(r.of(section(PAGE, ROOT_A, UPDATED)).level()).isEqualTo(Reliability.INTERPRETATION);
        assertThat(r.specification(UPDATED).origin()).isEqualTo(SourceOrigin.SPECIFICATIONS);

        reliability.save(ReliabilitySettings.defaults(P).with(SourceOrigin.ANTORA, Reliability.TO_CONFIRM));
        // changement pris en compte sans réindexation (FR-022)
        assertThat(resolver.forProject(P).of(section("https://doc/a.html", ANTORA, null)).level())
                .isEqualTo(Reliability.TO_CONFIRM);
    }

    @Test
    void pageAtteinteParDeuxRacinesPrendLeNiveauLePlusHaut() {
        sources.save(sources.find(ROOT_A).orElseThrow().withReliability(Reliability.TO_CONFIRM));
        sources.save(sources.find(ROOT_B).orElseThrow().withReliability(Reliability.REFERENCE));
        pages.replace(ROOT_A, Map.of("42", new PageState(3, UPDATED)));
        assertThat(resolver.forProject(P).of(section(PAGE + "#plafond", ROOT_A, UPDATED)).level())
                .isEqualTo(Reliability.TO_CONFIRM);

        pages.replace(ROOT_B, Map.of("42", new PageState(3, UPDATED)));
        assertThat(resolver.forProject(P).of(section(PAGE + "#plafond", ROOT_A, UPDATED)).level())
                .isEqualTo(Reliability.REFERENCE);

        // sans niveau propre, la racine prend celui de Confluence
        sources.save(sources.find(ROOT_B).orElseThrow().withReliability(null));
        reliability.save(ReliabilitySettings.defaults(P).with(SourceOrigin.CONFLUENCE, Reliability.TO_CONFIRM));
        assertThat(resolver.forProject(P).of(section(PAGE, ROOT_A, UPDATED)).level()).isEqualTo(Reliability.TO_CONFIRM);
    }

    @Test
    void sansSourcesNiReglages() {
        ProvenanceResolver.Resolution r = ProvenanceResolver.defaults().forProject(P);
        assertThat(r.of(section("https://doc/a.html", ANTORA, null)).origin()).isEqualTo(SourceOrigin.ANTORA);
        assertThat(r.of(SourceOrigin.ATTACHMENT, null).level()).isNull();
        assertThat(r.settings().uniform()).isFalse();
    }
}
