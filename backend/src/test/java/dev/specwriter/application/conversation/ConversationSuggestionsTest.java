package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 010 : exemples de questions tirés de la connaissance du projet, sans appel à l'IA. */
class ConversationSuggestionsTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-11-09T09:00:00Z");

    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final ConversationSuggestions suggestions = new ConversationSuggestions(index);

    @Test
    void sansConnaissanceAucunExemple() {
        assertThat(suggestions.of(P)).isEmpty();
    }

    @Test
    void unAdrEnVigueurUnTicketOuvertEtUnePageLesPlusRecents() {
        index.save(KnowledgeItem.adr(P, "adr/adr-003.html", 0, "ADR 003 : relances postales", "x",
                AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-004"), T.plusSeconds(500), T), null, null);
        index.save(KnowledgeItem.adr(P, "adr/adr-004.html", 0, "ADR 004 : relances par courriel", "x",
                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), T, T), null, null);
        index.save(KnowledgeItem.ticket(P, "AUR-12", "Émettre un avoir", "x", "Terminé", "done", "Story",
                T.plusSeconds(900), T), null, null);
        index.save(KnowledgeItem.ticket(P, "AUR-31", "Lettrage manuel des paiements", "x", "En cours", "indeterminate",
                "Story", T.plusSeconds(100), T), null, null);
        index.save(KnowledgeItem.ticket(P, "AUR-30", "Ancien ticket ouvert", "x", "À faire", "new", "Story", T, T),
                null, null);
        index.save(KnowledgeItem.docSection(P, "lettrage.html#delai", 1, "Lettrage > Délai", "x", null, T, T), null,
                null);
        index.save(KnowledgeItem.ticket(UUID.randomUUID(), "AUTRE-1", "Autre projet", "x", "À faire", "new", "Story",
                T.plusSeconds(9_000), T), null, null);

        assertThat(suggestions.of(P)).containsExactly(
                "Que décide l’ADR « ADR 004 : relances par courriel », et qu’est-ce que cela implique ?",
                "Où en est AUR-31 « Lettrage manuel des paiements », et qu’en dit la documentation ?",
                "La documentation « Lettrage » et les tickets sont-ils cohérents ?");
    }

    @Test
    void sansAdrUnElementDArchitectureEtTitresCoupes() {
        index.save(KnowledgeItem.archElement(P, new ArchElement("Facturation", ArchElement.Type.CONTAINER,
                ArchScope.PROJECT, false, "API de facturation", "Java"), T, T), null, null);
        index.save(KnowledgeItem.ticket(P, "AUR-40", "Relance postale des gros montants ".repeat(4), "x", "À faire", "new",
                "Story", T, T), null, null);

        List<String> out = suggestions.of(P);
        assertThat(out.getFirst()).isEqualTo("Quel rôle joue « Facturation » dans l’architecture du projet ?");
        assertThat(out.get(1)).contains("gros… »").hasSizeLessThan(140);
    }
}
