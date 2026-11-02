package dev.specwriter.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.search.UnifiedSearch.Item;
import dev.specwriter.application.search.UnifiedSearch.JiraStatus;
import dev.specwriter.application.search.UnifiedSearch.Provenance;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T098 : liste unique, provenance, et Jira absent, refusé ou en panne (FR-039 à FR-045). */
class UnifiedSearchTest {

    static final Instant T1 = Instant.parse("2026-10-29T10:00:00Z");
    static final Instant T2 = Instant.parse("2026-10-30T10:00:00Z");

    final JiraWorld w = new JiraWorld();
    UnifiedSearch search;
    UUID localOnly = UUID.randomUUID();
    Session linked;

    @BeforeEach
    void setUp() {
        search = new UnifiedSearch(w.local, w.versions, w.tickets, w.tracker, w.jira, Duration.ofMillis(300));
        linked = w.generatedSession();
        w.link(linked, "PROJ-2", "d");
        w.local.hits.add(new LocalSearch.Hit(localOnly, "Facturation", "…règle…", T1));
        w.local.hits.add(new LocalSearch.Hit(linked.id(), "Réservation", "…salle…", T1));
        w.tracker.searchResults.add(new JiraIssueSummary("PROJ-1", "Ticket seul", "À faire", "Story", T2));
        w.tracker.searchResults.add(new JiraIssueSummary("PROJ-2", "Titre Jira", "En cours", "Story", T2));
    }

    @Test
    void listeUniqueAvecProvenanceEtElementLieUneSeuleFois() {
        w.configureJira().grant(ConsentService.JIRA);
        UnifiedSearch.Result r = search.run("salle", 0);
        assertThat(r.jiraStatus()).isEqualTo(JiraStatus.OK);
        assertThat(r.items()).extracting(Item::id, Item::provenance).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(localOnly.toString(), Provenance.SPEC_WRITER),
                org.assertj.core.groups.Tuple.tuple(linked.id().toString(), Provenance.BOTH),
                org.assertj.core.groups.Tuple.tuple("PROJ-1", Provenance.JIRA));
        Item both = r.items().stream().filter(i -> i.provenance() == Provenance.BOTH).findFirst().orElseThrow();
        assertThat(both.title()).isEqualTo("Réservation");
        assertThat(both.jiraKey()).isEqualTo("PROJ-2");
        assertThat(both.sessionId()).isEqualTo(linked.id());
        assertThat(both.updatedAt()).isEqualTo(T2);
        assertThat(both.latestVersion()).isEqualTo(1);
        assertThat(r.items()).filteredOn(i -> i.id().equals(localOnly.toString())).singleElement()
                .satisfies(i -> assertThat(i.latestVersion()).isNull());
        assertThat(r.items()).allSatisfy(i -> {
            assertThat(i.id()).isNotBlank();
            assertThat(i.title()).isNotBlank();
            assertThat(i.updatedAt()).isNotNull();
        });
        assertThat(r.items().getLast().id()).isEqualTo(localOnly.toString());
        assertThat(w.tracker.calls).containsExactly("search PROJ salle");
    }

    @Test
    void jiraNonConfigure() {
        UnifiedSearch.Result r = search.run("salle", 0);
        assertThat(r.jiraStatus()).isEqualTo(JiraStatus.NOT_CONFIGURED);
        assertThat(r.items()).hasSize(2);
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void sansConsentementJiraPasAppele() {
        w.configureJira();
        UnifiedSearch.Result r = search.run("salle", 0);
        assertThat(r.jiraStatus()).isEqualTo(JiraStatus.CONSENT_REQUIRED);
        assertThat(r.items()).extracting(Item::provenance).containsOnly(Provenance.SPEC_WRITER, Provenance.BOTH);
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void jiraEnErreurOuTropLentResultatsLocauxConserves() {
        w.configureJira().grant(ConsentService.JIRA);
        w.tracker.failure = Kind.UNREACHABLE;
        assertThat(search.run("salle", 0)).satisfies(r -> {
            assertThat(r.jiraStatus()).isEqualTo(JiraStatus.UNAVAILABLE);
            assertThat(r.items()).hasSize(2);
        });
        w.tracker.failure = null;
        w.tracker.searchDelayMillis = 2000;
        long start = System.nanoTime();
        assertThat(search.run("salle", 0).jiraStatus()).isEqualTo(JiraStatus.UNAVAILABLE);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1500);
    }

    @Test
    void jetonRefuseOuAbsent() {
        w.configureJira().grant(ConsentService.JIRA);
        w.tracker.failure = Kind.UNAUTHORIZED;
        assertThat(search.run("salle", 0).jiraStatus()).isEqualTo(JiraStatus.TOKEN_REJECTED);
        w.secrets.delete(SecretStore.JIRA_PAT);
        assertThat(search.run("salle", 0).jiraStatus()).isEqualTo(JiraStatus.TOKEN_REJECTED);
    }

    @Test
    void pagesSuivantesUniquementJira() {
        w.configureJira().grant(ConsentService.JIRA);
        UnifiedSearch.Result r = search.run("salle", 1);
        assertThat(r.items()).extracting(Item::id).containsExactlyInAnyOrder("PROJ-1", linked.id().toString());
        assertThat(r.jiraTotal()).isEqualTo(2);
        assertThat(w.local.queries).isEmpty();
    }

    @Test
    void longueurDeLaSaisie() {
        assertThatThrownBy(() -> search.run(" a ", 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> search.run("x".repeat(201), 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> search.run(null, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> search.run("salle", -1)).isInstanceOf(DomainException.class);
    }
}
