package dev.specwriter.infrastructure.persistence.search;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Recherche FTS5 (FR-040) et ticket lié. */
@QuarkusTest
class LocalSearchIT {

    static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");

    @Inject
    LocalSearch search;
    @Inject
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    LinkedTicketRepository tickets;

    Session session(String need, String name) {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID, Need.of(need), name, T0);
        sessions.save(s, Questionnaire.empty());
        return s;
    }

    @Test
    void titreBesoinEtMarkdownAvecAccentsIgnores() {
        String marker = "zq" + UUID.randomUUID().toString().substring(0, 6);
        Session s = session("Chaque règle de facturation doit être appliquée aux commandes du mois " + marker + " sans exception.",
                "Facturation " + marker);
        assertThat(search.search("regle " + marker, 10)).extracting(LocalSearch.Hit::sessionId).containsExactly(s.id());
        assertThat(search.search("RÈGLE " + marker, 10)).hasSize(1);
        assertThat(search.search("factu " + marker, 10)).hasSize(1);
        assertThat(search.search("introuvable " + marker, 10)).isEmpty();

        // une nouvelle version met l'index à jour (trigger)
        assertThat(search.search("Paiement " + marker, 10)).isEmpty();
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 1, Specs.valid(), "fp", VersionOrigin.GENERATED,
                "# Paiement en ligne\n\nLe client paie " + marker, T0));
        assertThat(search.search("paiement " + marker, 10)).singleElement()
                .satisfies(h -> assertThat(h.excerpt()).isNotBlank());

        // renommage et suppression suivent aussi
        sessions.save(s.rename("Renommée " + marker, T0), Questionnaire.empty());
        assertThat(search.search("renommee " + marker, 10)).singleElement()
                .satisfies(h -> assertThat(h.title()).isEqualTo("Renommée " + marker));
        sessions.delete(s.id());
        assertThat(search.search(marker, 10)).isEmpty();
    }

    @Test
    void saisieNonInterpreteeCommeSyntaxeFts() {
        String marker = "zw" + UUID.randomUUID().toString().substring(0, 6);
        session("Les employés doivent pouvoir réserver une salle de réunion " + marker + " pour une durée.", null);
        assertThat(search.search("salle\" OR \"x", 10)).isNotNull();
        assertThat(search.search(marker + " (salle) * : ^ réunion\"", 10)).hasSize(1);
        assertThat(search.search("\"*(){}", 10)).isEmpty();
        assertThat(FtsLocalSearch.toMatch("a \"b\" c*")).isEqualTo("\"a\"* \"b\"* \"c\"*");
    }

    @Test
    void moinsDe300msSurMilleSessions() {
        String marker = "zp" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 1000; i++) {
            session("Session de charge numéro " + i + " pour mesurer la recherche plein texte " + marker + " rapidement.", null);
        }
        search.search("charge " + marker, 50);
        long start = System.nanoTime();
        assertThat(search.search("charge " + marker, 50)).hasSize(50);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(300);
    }

    @Test
    void ticketLieEnregistreEtRelu() {
        Session s = session("Les clients doivent pouvoir payer leur panier en ligne par carte bancaire sans frais.", null);
        String key = "PROJ-" + Math.abs(s.id().getLeastSignificantBits() % 100000);
        LinkedJiraTicket t = new LinkedJiraTicket(s.id(), key, "https://jira/browse/" + key, "Story", 1, T0,
                ConflictDetector.hash("d"), T0, null, LinkedJiraTicket.SyncState.IN_SYNC);
        tickets.save(t);
        assertThat(tickets.findBySession(s.id())).contains(t);
        assertThat(tickets.findByIssueKey(key)).contains(t);
        tickets.save(t.localAhead());
        assertThat(tickets.findBySession(s.id()).orElseThrow().syncState()).isEqualTo(LinkedJiraTicket.SyncState.LOCAL_AHEAD);
        assertThat(tickets.all()).anyMatch(x -> x.issueKey().equals(key));
        sessions.delete(s.id());
        assertThat(tickets.findByIssueKey(key)).isEmpty();
    }
}
