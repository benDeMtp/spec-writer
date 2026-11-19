package fr.kawamind.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.settings.ConfluenceAccess;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluencePageStates.PageState;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.SecretPersistence;
import fr.kawamind.support.FakeConfluenceClient;
import fr.kawamind.support.InMemoryConfluencePages;
import fr.kawamind.support.InMemoryConfluenceSettings;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemorySecrets;
import fr.kawamind.support.JiraWorld;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T022 (016) : aperçu, ajout et suppression des racines Confluence d'un projet. */
class ManageConfluenceRootsTest {

    static final UUID P = Project.DEFAULT_ID;
    static final String S = FakeConfluenceClient.SERVER;
    static final String ROOT = S + "/pages/viewpage.action?pageId=100";

    final JiraWorld w = new JiraWorld();
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final FakeConfluenceClient wiki = new FakeConfluenceClient();
    final InMemoryConfluenceSettings settings = new InMemoryConfluenceSettings();
    final InMemorySecrets secrets = new InMemorySecrets();
    final InMemoryConfluencePages pages = new InMemoryConfluencePages(w.docSources);
    final ManageDocSources sources = new ManageDocSources(w.projects, w.docSources, w.ids, index, w.folder,
            new ConfluenceAccess(settings, secrets), wiki, pages);

    @BeforeEach
    void setUp() {
        settings.save(ConfluenceSettings.create(S, null, false, SecretPersistence.KEYCHAIN));
        secrets.put(SecretStore.CONFLUENCE_PAT, "PAT");
        Instant at = Instant.parse("2026-09-01T07:00:00Z");
        wiki.page("100", null, "Remboursements", 1, at, "")
                .page("101", "100", "Frais", 1, at, "")
                .page("105", "101", "Kilométriques", 1, at, "")
                .page("200", null, "Autre espace", 1, at, "");
    }

    @Test
    void apercuTitreEtNombreDePages() {
        ManageDocSources.ConfluencePreview preview = sources.previewConfluence(P, ROOT);
        assertThat(preview.problem()).isNull();
        assertThat(preview.title()).isEqualTo("Remboursements");
        assertThat(preview.pages()).isEqualTo(3);
        // rien n'est lu au-delà de la liste
        assertThat(wiki.contentReads()).isZero();
    }

    @Test
    void refusExpliques() {
        assertThat(sources.previewConfluence(P, "https://autre.example.com/pages/viewpage.action?pageId=100").problem())
                .isEqualTo("OTHER_HOST");
        assertThat(sources.previewConfluence(P, S + "/dashboard.action").problem()).isEqualTo("NOT_A_PAGE");
        assertThat(sources.previewConfluence(P, S + "/pages/viewpage.action?pageId=999").problem())
                .isEqualTo("NOT_A_PAGE");
        wiki.down = ConfluenceClient.Failure.UNAUTHORIZED;
        assertThat(sources.previewConfluence(P, ROOT).problem()).isEqualTo("ACCESS_DENIED");
        wiki.down = ConfluenceClient.Failure.UNREACHABLE;
        assertThat(sources.previewConfluence(P, ROOT).problem()).isEqualTo("UNREACHABLE");
        assertThatThrownBy(() -> sources.addConfluence(P, ROOT, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.userMessage()).contains("Confluence ne répond pas"));
    }

    @Test
    void sansServeurDeclareLienVersConfiguration() {
        settings.delete();
        assertThat(sources.previewConfluence(P, ROOT).problem()).isEqualTo("CONFLUENCE_NOT_CONFIGURED");
        assertThatThrownBy(() -> sources.addConfluence(P, ROOT, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLUENCE_NOT_CONFIGURED));
    }

    @Test
    void racineTropLargeAuDelaDeDeuxMillePagesPourLeProjet() {
        UUID other = UUID.randomUUID();
        w.docSources.save(DocSource.confluence(other, P, S + "/pages/viewpage.action?pageId=200", "200", "Autre", null));
        java.util.Map<String, PageState> read = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 1_998; i++) {
            read.put(String.valueOf(5_000 + i), new PageState(1, null));
        }
        pages.replace(other, read);

        ManageDocSources.ConfluencePreview preview = sources.previewConfluence(P, ROOT);

        assertThat(preview.problem()).isEqualTo("TOO_LARGE");
        assertThat(preview.pages()).isEqualTo(3);
        assertThatThrownBy(() -> sources.addConfluence(P, ROOT, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.userMessage()).contains("3 pages").contains("2000"));
    }

    @Test
    void ajoutQuiGardeLaPageEtLeTitre() {
        DocSource added = sources.add(P, DocSource.Kind.CONFLUENCE, ROOT, java.util.List.of());
        assertThat(added.kind()).isEqualTo(DocSource.Kind.CONFLUENCE);
        assertThat(added.pageId()).isEqualTo("100");
        assertThat(added.title()).isEqualTo("Remboursements");
        assertThat(added.reliability()).isNull();

        DocSource levelled = sources.setReliability(P, added.id(), Reliability.TO_CONFIRM);
        assertThat(levelled.reliability()).isEqualTo(Reliability.TO_CONFIRM);
        assertThat(w.docSources.find(added.id()).orElseThrow().reliability()).isEqualTo(Reliability.TO_CONFIRM);

        assertThatThrownBy(() -> sources.addConfluence(P, ROOT + "&focus=1", null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("déjà"));
    }

    @Test
    void niveauProprePasHorsConfluence() {
        DocSource site = sources.add(P, DocSource.Kind.URL, "https://doc.example.com/");
        assertThatThrownBy(() -> sources.setReliability(P, site.id(), Reliability.REFERENCE))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void limiteDeDixSourcesInchangee() {
        for (int i = 0; i < DocSource.MAX_PER_PROJECT; i++) {
            sources.add(P, DocSource.Kind.URL, "https://doc" + i + ".example.com/");
        }
        assertThatThrownBy(() -> sources.addConfluence(P, ROOT, null)).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("10 sources"));
    }

    @Test
    void suppressionGardeLesPagesDUneAutreRacine() {
        DocSource a = sources.addConfluence(P, ROOT, null);
        DocSource b = sources.addConfluence(P, S + "/pages/viewpage.action?pageId=101", null);
        String shared = ConfluenceClient.pageRef(S, "105") + "#0";
        String only = ConfluenceClient.pageRef(S, "100") + "#0";
        index.save(KnowledgeItem.docSection(P, shared, 0, "Kilométriques", "Barème.", null, null, null), null, null);
        index.save(KnowledgeItem.docSection(P, only, 0, "Remboursements", "Racine.", null, null, null), null, null);
        index.replaceSource(P, a.id(), Set.of(key(shared), key(only)));
        pages.replace(a.id(), Map.of("100", new PageState(1, null), "105", new PageState(1, null)));
        pages.replace(b.id(), Map.of("105", new PageState(1, null)));

        sources.delete(P, a.id());

        assertThat(index.find(P, key(shared))).isPresent();
        assertThat(index.find(P, key(only))).isEmpty();
    }

    static KnowledgeIndex.Key key(String ref) {
        return new KnowledgeIndex.Key(KnowledgeKind.DOC_SECTION, ref, 0);
    }
}
