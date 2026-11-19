package fr.kawamind.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.settings.ConfluenceAccess;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.KnowledgeIndex.Key;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.SecretPersistence;
import fr.kawamind.support.FakeConfluenceClient;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConfluencePages;
import fr.kawamind.support.InMemoryConfluenceSettings;
import fr.kawamind.support.InMemoryDocSources;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemorySecrets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T023, T061 (016) : lecture des racines Confluence, réindexation incrémentale (R3), limites (R10). */
class ConfluenceIndexationTest {

    static final UUID P = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    static final String S = FakeConfluenceClient.SERVER;
    static final Instant SEPT = Instant.parse("2026-09-01T07:00:00Z");

    final FakeConfluenceClient wiki = new FakeConfluenceClient();
    final InMemoryDocSources sources = new InMemoryDocSources();
    final InMemoryConfluencePages pages = new InMemoryConfluencePages(sources);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final InMemoryConfluenceSettings settings = new InMemoryConfluenceSettings();
    final InMemorySecrets secrets = new InMemorySecrets();
    final FixedClock clock = new FixedClock(Instant.parse("2026-11-17T10:00:00Z"));
    final ConfluenceIndexation indexation = new ConfluenceIndexation(new ConfluenceAccess(settings, secrets), wiki,
            pages, sources, index, clock);

    static final class Recorder implements IndexationProgress {
        final List<IndexationError> errors = new ArrayList<>();

        @Override
        public void step(Step step, int total) {
        }

        @Override
        public void advance(String current, int done, int total) {
        }

        @Override
        public void error(IndexationError error) {
            errors.add(error);
        }
    }

    Recorder progress = new Recorder();

    @BeforeEach
    void setUp() {
        settings.save(ConfluenceSettings.create(S, null, false, SecretPersistence.KEYCHAIN));
        secrets.put(SecretStore.CONFLUENCE_PAT, "PAT");
        wiki.page("100", null, "Remboursements", 1, SEPT, "Racine.")
                .page("101", "100", "Frais de déplacement", 1, SEPT, "Déplacements.")
                .page("105", "101", "Frais kilométriques", 1, SEPT, "Barème kilométrique.")
                .page("102", "100", "Frais de repas", 1, SEPT, "Repas.")
                .page("103", "100", "Notes de frais", 2, Instant.parse("2026-10-03T09:00:00Z"), "Plafond 1 000 euros.")
                .page("109", "103", "Paiement", 1, SEPT, "Virement.")
                .page("111", "100", "Confidentiel", 1, SEPT, "Restreint.")
                .page("900", null, "Hors arbre", 1, SEPT, "Cantine.");
        wiki.forbidden.add("111");
        sources.save(DocSource.confluence(A, P, S + "/pages/viewpage.action?pageId=100", "100", "Remboursements", null));
        sources.save(DocSource.confluence(B, P, S + "/pages/viewpage.action?pageId=103", "103", "Notes de frais", null));
    }

    Set<String> indexedPages() {
        return index.all(P, KnowledgeKind.DOC_SECTION).stream()
                .map(i -> ConfluenceClient.pageId(i.ref()).orElse("?")).collect(Collectors.toSet());
    }

    ConfluenceIndexation.Outcome run(SynchronizeKnowledge.Mode mode, UUID... roots) {
        progress = new Recorder();
        return indexation.run(P, List.of(roots).stream().map(r -> sources.find(r).orElseThrow()).toList(), mode,
                progress);
    }

    @Test
    void arbreCompletIndexeRienHorsArbre() {
        ConfluenceIndexation.Outcome outcome = run(SynchronizeKnowledge.Mode.FULL, A);

        assertThat(indexedPages()).containsExactlyInAnyOrder("100", "101", "105", "102", "103", "109");
        assertThat(outcome.complete()).isTrue();
        // page restreinte : ignorée et comptée
        assertThat(progress.errors).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(IndexationError.Kind.PAGE);
            assertThat(e.reason()).isEqualTo("ACCESS_DENIED");
        });
        assertThat(sources.find(A).orElseThrow().lastResult().pagesInError()).isEqualTo(1);
        assertThat(sources.find(A).orElseThrow().lastReadAt()).isEqualTo(clock.now());
        // date de la version, jamais celle de l'indexation (FR-012)
        assertThat(index.find(P, new Key(KnowledgeKind.DOC_SECTION, ConfluenceClient.pageRef(S, "103") + "#0", 0)))
                .get().extracting(KnowledgeItem::sourceUpdatedAt).isEqualTo(Instant.parse("2026-10-03T09:00:00Z"));
        assertThat(outcome.sections()).hasSize(6);
    }

    @Test
    void reindexationSansChangementNeRelitRien() {
        run(SynchronizeKnowledge.Mode.FULL, A);
        long before = wiki.contentReads();

        run(SynchronizeKnowledge.Mode.INCREMENTAL, A);

        // seule la page restreinte, jamais lue, est retentée
        assertThat(wiki.contentReads() - before).isEqualTo(1);
        assertThat(indexedPages()).hasSize(6);
    }

    @Test
    void seuleLaPageDontLaVersionChangeEstRelue() {
        run(SynchronizeKnowledge.Mode.FULL, A);
        wiki.page("105", "101", "Frais kilométriques", 2, Instant.parse("2026-11-01T08:00:00Z"), "Nouveau barème.");
        wiki.calls.clear();

        run(SynchronizeKnowledge.Mode.INCREMENTAL, A);

        assertThat(wiki.calls).filteredOn(c -> c.startsWith("content")).containsExactlyInAnyOrder("content 105",
                "content 111");
        assertThat(index.find(P, new Key(KnowledgeKind.DOC_SECTION, ConfluenceClient.pageRef(S, "105") + "#0", 0)))
                .get().extracting(KnowledgeItem::body).isEqualTo("Nouveau barème.");
    }

    @Test
    void pageSupprimeeRetiree() {
        run(SynchronizeKnowledge.Mode.FULL, A);
        wiki.nodes.remove("102");

        run(SynchronizeKnowledge.Mode.INCREMENTAL, A);

        assertThat(indexedPages()).doesNotContain("102").contains("101", "105");
    }

    @Test
    void pageCommuneADeuxRacinesLueUneFois() {
        run(SynchronizeKnowledge.Mode.FULL, A, B);

        assertThat(wiki.calls.stream().filter(c -> c.equals("content 103"))).hasSize(1);
        assertThat(indexedPages()).containsExactlyInAnyOrder("100", "101", "105", "102", "103", "109");
        assertThat(pages.roots(P).get("109")).containsExactlyInAnyOrder(A, B);
    }

    @Test
    void pageSortieDUneRacineMaisEncoreSousUneAutreGardee() {
        run(SynchronizeKnowledge.Mode.FULL, A, B);
        // « Notes de frais » quitte l'arbre de A, mais reste la racine de B
        wiki.page("103", "900", "Notes de frais", 2, Instant.parse("2026-10-03T09:00:00Z"), "Plafond 1 000 euros.");

        run(SynchronizeKnowledge.Mode.INCREMENTAL, A, B);

        assertThat(indexedPages()).contains("103", "109");
        assertThat(pages.roots(P).get("103")).containsExactly(B);
    }

    @Test
    void racineDisparueRetireSesPages() {
        run(SynchronizeKnowledge.Mode.FULL, A);
        wiki.nodes.remove("100");

        ConfluenceIndexation.Outcome outcome = run(SynchronizeKnowledge.Mode.INCREMENTAL, A);

        assertThat(outcome.complete()).isFalse();
        assertThat(progress.errors).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(IndexationError.Kind.SOURCE);
            assertThat(e.reason()).isEqualTo("NOT_FOUND");
        });
        assertThat(indexedPages()).isEmpty();
    }

    @Test
    void confluenceInjoignableGardeLesPages() {
        run(SynchronizeKnowledge.Mode.FULL, A);
        wiki.down = ConfluenceClient.Failure.UNREACHABLE;

        ConfluenceIndexation.Outcome outcome = run(SynchronizeKnowledge.Mode.FULL, A);

        assertThat(outcome.complete()).isFalse();
        assertThat(indexedPages()).hasSize(6);
        assertThat(outcome.sections()).hasSize(6);
        assertThat(progress.errors).singleElement().satisfies(e -> {
            assertThat(e.reason()).isEqualTo("UNREACHABLE");
            assertThat(e.message()).contains("Dernière lecture réussie : 2026-11-17T10:00:00Z");
        });
    }

    @Test
    void sansServeurDeclareLesPagesRestent() {
        run(SynchronizeKnowledge.Mode.FULL, A);
        settings.delete();

        ConfluenceIndexation.Outcome outcome = run(SynchronizeKnowledge.Mode.FULL, A);

        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.sections()).hasSize(6);
        assertThat(progress.errors).extracting(IndexationError::reason).containsExactly("CONFLUENCE_NOT_CONFIGURED");
    }

    /** T061 : limite de 2 000 pages par projet ; 500 pages lues en 5 requêtes de liste au rythme de 8 par seconde. */
    @Test
    void limiteDuProjetEtRequetesDeListe() {
        wiki.nodes.clear();
        wiki.forbidden.clear();
        wiki.page("100", null, "Racine", 1, SEPT, "Racine.");
        for (int i = 0; i < 2_100; i++) {
            wiki.page(String.valueOf(10_000 + i), "100", "Page " + i, 1, SEPT, "Texte " + i);
        }

        ConfluenceIndexation.Outcome outcome = run(SynchronizeKnowledge.Mode.FULL, A);

        assertThat(outcome.complete()).isFalse();
        assertThat(progress.errors).extracting(IndexationError::reason).containsExactly("LIMIT_REACHED");
        assertThat(indexedPages()).hasSize(ConfluenceIndexation.MAX_PAGES);
    }


    /**
     * T061 (SC-003, R10) : 500 pages se lisent en moins de 5 minutes au débit de 8 requêtes par
     * seconde ; une resynchronisation sans changement ne fait que les requêtes de liste.
     */
    @Test
    void cinqCentsPagesSousCinqMinutesEtResynchronisationParLesSeulesListes() {
        wiki.nodes.clear();
        wiki.forbidden.clear();
        wiki.pageSize = 100;
        wiki.page("100", null, "Remboursements", 1, SEPT, "Racine.");
        for (int i = 1; i < 500; i++) {
            wiki.page(String.valueOf(1000 + i), i < 20 ? "100" : String.valueOf(1000 + i % 19 + 1), "Page " + i, 1,
                    SEPT, "Texte de la page " + i + ".");
        }

        run(SynchronizeKnowledge.Mode.FULL, A);
        int requests = wiki.calls.size();
        assertThat(indexedPages()).hasSize(500);
        // racine, 5 listes de 100, 500 lectures
        assertThat(requests).isLessThanOrEqualTo(510);
        assertThat(requests / 8.0).as("secondes au débit de 8 requêtes par seconde").isLessThan(300);

        wiki.calls.clear();
        run(SynchronizeKnowledge.Mode.INCREMENTAL, A);
        assertThat(wiki.contentReads()).isZero();
        assertThat(wiki.calls).allMatch(c -> c.startsWith("descendants ") || c.startsWith("page ") || c.startsWith("resolve"))
                .hasSizeLessThanOrEqualTo(6);
    }
}
