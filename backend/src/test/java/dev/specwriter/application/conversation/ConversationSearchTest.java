package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.FakeLocalSearch;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 010, research.md R3 : connaissance du projet et spécifications, fusionnées, bornées. */
class ConversationSearchTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-11-09T09:00:00Z");

    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final FakeLocalSearch local = new FakeLocalSearch();
    ConversationSearch search;

    @BeforeEach
    void setUp() {
        search = new ConversationSearch(index, new FakeEmbedding(), local);
        index.save(KnowledgeItem.ticket(P, "FACT-7", "Émettre un avoir", "L'avoir est émis par le service Avoirs.",
                "Terminé", "done", "Story", T, T), null, null);
        index.save(KnowledgeItem.adr(P, "adr/0005.html", 0, "5. Bus d'événements pour les avoirs",
                "Les avoirs passent par le bus. " + "x".repeat(3_000), AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), T, T),
                null, null);
        index.save(KnowledgeItem.adr(P, "adr/0005.html", 1, "5. Bus d'événements pour les avoirs", "suite avoirs", AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), T, T),
                null, null);
        index.save(KnowledgeItem.ticket(UUID.randomUUID(), "AUTRE-1", "Avoir d'un autre projet", "avoirs", "Terminé",
                "done", "Story", T, T), null, null);
        UUID spec = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        local.specs.add(new LocalSearch.SpecHit(spec, 3, "Saisir un avoir", "# Saisir un avoir\n\nLes avoirs…", true));
    }

    @Test
    void connaissanceEtSpecificationsFusionnees() {
        List<ConversationSearch.Found> found = search.run(P, "émission des avoirs", Set.of());

        assertThat(found).extracting(ConversationSearch.Found::ref)
                .containsExactlyInAnyOrder("FACT-7", "adr/0005.html", "spec:00000000-0000-0000-0000-00000000000a@v3");
        // un document en plusieurs morceaux est un seul résultat, son extrait est borné
        assertThat(found).filteredOn(f -> f.kind() == SourceKind.ADR).singleElement()
                .satisfies(f -> assertThat(f.text().length()).isLessThanOrEqualTo(KnowledgeBudget.SOURCE_EXCERPT));
        assertThat(found).filteredOn(f -> f.kind() == SourceKind.SPECIFICATION).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("Saisir un avoir");
            assertThat(f.text()).startsWith("(feature implémentée)");
        });
        assertThat(found).filteredOn(f -> f.kind() == SourceKind.TICKET).singleElement()
                .satisfies(f -> assertThat(f.text()).contains("Terminé"));
    }

    @Test
    void sortesDemandeesEtPlafond() {
        assertThat(search.run(P, "avoirs", Set.of(SourceKind.SPECIFICATION))).extracting(ConversationSearch.Found::kind)
                .containsOnly(SourceKind.SPECIFICATION);
        assertThat(search.run(P, "avoirs", Set.of(SourceKind.TICKET))).extracting(ConversationSearch.Found::kind)
                .containsOnly(SourceKind.TICKET);
        for (int i = 0; i < 20; i++) {
            index.save(KnowledgeItem.ticket(P, "FACT-" + (100 + i), "Avoir " + i, "avoirs", "À faire", "new", "Story", T, T),
                    null, null);
        }
        assertThat(search.run(P, "avoirs", Set.of())).hasSize(ConversationSearch.MAX_RESULTS);
    }
}
