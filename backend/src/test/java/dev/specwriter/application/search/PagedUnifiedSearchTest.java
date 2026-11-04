package dev.specwriter.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.search.UnifiedSearch.Item;
import dev.specwriter.application.search.UnifiedSearch.Page;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T042 : liste unique paginée par 25, fusion par date des deux sources, curseur stable et signé
 * (FR-021a, FR-022 de 002, research.md R9).
 */
class PagedUnifiedSearchTest {

    static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA);
    UnifiedSearch search;
    /** Tous les éléments attendus, du plus récent au plus ancien. */
    final List<String> expected = new ArrayList<>();
    Session linked;

    /** 30 sessions locales (heures paires) et 40 tickets Jira (heures impaires), entremêlés par date. */
    @BeforeEach
    void setUp() {
        search = new UnifiedSearch(w.local, w.versions, w.tickets, w.tracker, w.jira, w.projects, Duration.ofSeconds(2));
        List<Object[]> all = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            UUID id = UUID.randomUUID();
            Instant at = T0.plus(Duration.ofHours(2L * i));
            w.local.hits.add(new LocalSearch.Hit(id, "Spec " + i, "…", at));
            all.add(new Object[] {id.toString(), at});
        }
        for (int i = 0; i < 40; i++) {
            Instant at = T0.plus(Duration.ofHours(2L * i + 1));
            all.add(new Object[] {"PROJ-" + i, at});
        }
        all.sort(Comparator.comparing((Object[] o) -> (Instant) o[1]).reversed());
        all.forEach(o -> expected.add((String) o[0]));
        // les tickets Jira, du plus récent au plus ancien, comme les renvoie la JQL
        for (int i = 39; i >= 0; i--) {
            w.tracker.searchResults.add(new JiraIssueSummary("PROJ-" + i, "Ticket " + i, "À faire", "Story",
                    T0.plus(Duration.ofHours(2L * i + 1))));
        }
    }

    static List<String> ids(Page p) {
        return p.items().stream().map(Item::id).toList();
    }

    @Test
    void fusionParDateSansDoublonNiElementSaute() {
        Page first = search.run("facture", null);
        assertThat(first.items()).hasSize(25);
        assertThat(first.position()).isEqualTo("1 à 25 sur 70");
        assertThat(first.approximateTotal()).isFalse();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(first.items()).isSortedAccordingTo(Comparator.comparing(Item::updatedAt).reversed());

        List<String> seen = new ArrayList<>(ids(first));
        Page page = first;
        List<String> positions = new ArrayList<>(List.of(first.position()));
        while (page.nextCursor() != null) {
            page = search.run("facture", page.nextCursor());
            seen.addAll(ids(page));
            positions.add(page.position());
        }
        assertThat(seen).containsExactlyElementsOf(expected);
        assertThat(positions).containsExactly("1 à 25 sur 70", "26 à 50 sur 70", "51 à 70 sur 70");
        // jamais plus de 25 tickets demandés à Jira par page
        assertThat(w.tracker.searchPages).allSatisfy(p -> assertThat(p).endsWith("+25"));
    }

    @Test
    void unTicketLieNApparaitQuUneFoisCoteLocal() {
        linked = w.generatedSession();
        w.link(linked, "PROJ-39", "d");
        w.local.hits.add(new LocalSearch.Hit(linked.id(), "Spec liée", "…", T0.plus(Duration.ofDays(30))));
        Page first = search.run("facture", null);
        assertThat(ids(first)).first().isEqualTo(linked.id().toString());
        assertThat(first.items().getFirst().provenance()).isEqualTo(UnifiedSearch.Provenance.BOTH);
        assertThat(first.items().getFirst().jiraKey()).isEqualTo("PROJ-39");
        List<String> seen = new ArrayList<>(ids(first));
        Page page = first;
        while (page.nextCursor() != null) {
            page = search.run("facture", page.nextCursor());
            seen.addAll(ids(page));
        }
        assertThat(seen).doesNotHaveDuplicates().doesNotContain("PROJ-39").hasSize(70);
        assertThat(first.approximateTotal()).isTrue();
    }

    @Test
    void unTicketLieDontLaSessionNeCorrespondPasEstMontreParJira() {
        linked = w.generatedSession();
        w.link(linked, "PROJ-39", "d");
        Page first = search.run("facture", null);
        Item item = first.items().stream().filter(i -> "PROJ-39".equals(i.jiraKey())).findFirst().orElseThrow();
        assertThat(item.id()).isEqualTo(linked.id().toString());
        assertThat(item.provenance()).isEqualTo(UnifiedSearch.Provenance.BOTH);
        assertThat(item.latestVersion()).isEqualTo(1);
    }

    @Test
    void jiraEnErreurLaPageLocaleEstRendueEtLeCurseurJiraNAvancePas() {
        Page first = search.run("facture", null);
        w.tracker.failure = Kind.UNREACHABLE;
        Page second = search.run("facture", first.nextCursor());
        assertThat(second.jiraError()).isNotBlank();
        assertThat(second.jiraStatus()).isEqualTo(UnifiedSearch.JiraStatus.UNAVAILABLE);
        assertThat(second.items()).allSatisfy(i -> assertThat(i.provenance()).isEqualTo(UnifiedSearch.Provenance.SPEC_WRITER));
        assertThat(second.approximateTotal()).isTrue();

        // Jira revient : la page suivante reprend les tickets là où ils s'étaient arrêtés
        w.tracker.failure = null;
        List<String> seen = new ArrayList<>(ids(first));
        seen.addAll(ids(second));
        Page page = second;
        while (page.nextCursor() != null) {
            page = search.run("facture", page.nextCursor());
            seen.addAll(ids(page));
        }
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void curseurModifieOuDUneAutreRechercheRefuse() {
        String cursor = search.run("facture", null).nextCursor();
        String[] parts = cursor.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"l\":0,\"j\":0,\"s\":0,\"q\":\"x\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + parts[1];
        for (String bad : List.of(forged, cursor + "x", "nimportequoi", cursor.replace('.', '-'))) {
            assertThatThrownBy(() -> search.run("facture", bad)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThatThrownBy(() -> search.run("salle", cursor)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void sansJiraLaListeEstLocaleEtExacte() {
        JiraWorld bare = new JiraWorld();
        bare.local.hits.addAll(w.local.hits);
        UnifiedSearch local = new UnifiedSearch(bare.local, bare.versions, bare.tickets, bare.tracker, bare.jira,
                bare.projects, Duration.ofSeconds(2));
        Page first = local.run("facture", null);
        assertThat(first.jiraStatus()).isEqualTo(UnifiedSearch.JiraStatus.NOT_CONFIGURED);
        assertThat(first.jiraError()).isNull();
        assertThat(first.position()).isEqualTo("1 à 25 sur 30");
        assertThat(local.run("facture", first.nextCursor()).position()).isEqualTo("26 à 30 sur 30");
        assertThat(bare.tracker.calls).isEmpty();
    }
}
