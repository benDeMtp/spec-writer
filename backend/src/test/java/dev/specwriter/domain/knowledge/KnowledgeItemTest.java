package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeItemTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    private static KnowledgeItem ticket(String statusCategory) {
        return KnowledgeItem.ticket(PROJECT, "FACT-7", "Annuler une facture", "Par un avoir total.", "Terminé",
                statusCategory, "Story", NOW, NOW);
    }

    @Test
    void lesQuatreSortesDElements() {
        assertThat(KnowledgeKind.values()).containsExactly(KnowledgeKind.TICKET, KnowledgeKind.DOC_SECTION,
                KnowledgeKind.ADR, KnowledgeKind.ARCH_ELEMENT);
    }

    @Test
    void titreDe500CaracteresMaximum() {
        assertThat(KnowledgeItem.docSection(PROJECT, "u#a", 0, "t".repeat(500), "corps", null, NOW, NOW).title())
                .hasSize(500);
        assertThatThrownBy(() -> KnowledgeItem.docSection(PROJECT, "u#a", 0, "t".repeat(501), "corps", null, NOW, NOW))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("500"));
    }

    @Test
    void corpsDe8000CaracteresMaximum() {
        assertThat(KnowledgeItem.docSection(PROJECT, "u#a", 0, "t", "c".repeat(8_000), null, NOW, NOW).body())
                .hasSize(8_000);
        assertThatThrownBy(() -> KnowledgeItem.docSection(PROJECT, "u#a", 0, "t", "c".repeat(8_001), null, NOW, NOW))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("8 000"));
    }

    @Test
    void refEtTitreObligatoires() {
        assertThatThrownBy(() -> KnowledgeItem.docSection(PROJECT, " ", 0, "t", "c", null, NOW, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> KnowledgeItem.docSection(PROJECT, "u", 0, "", "c", null, NOW, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> KnowledgeItem.docSection(PROJECT, "u", -1, "t", "c", null, NOW, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void empreinteSha256DuTitreEtDuCorps() {
        KnowledgeItem a = ticket("done");
        assertThat(a.contentHash()).matches("[0-9a-f]{64}");
        assertThat(ticket("new").contentHash()).isEqualTo(a.contentHash());
        KnowledgeItem other = KnowledgeItem.ticket(PROJECT, "FACT-7", "Annuler une facture", "Par un avoir partiel.",
                "Terminé", "done", "Story", NOW, NOW);
        assertThat(other.contentHash()).isNotEqualTo(a.contentHash());
        // le séparateur évite qu'un déplacement entre titre et corps garde la même empreinte
        assertThat(KnowledgeItem.docSection(PROJECT, "u", 0, "ab", "c", null, NOW, NOW).contentHash())
                .isNotEqualTo(KnowledgeItem.docSection(PROJECT, "u", 0, "a", "bc", null, NOW, NOW).contentHash());
    }

    @Test
    void ticketFermeSiCategorieDone() {
        assertThat(ticket("done").closed()).isTrue();
        assertThat(ticket("indeterminate").closed()).isFalse();
        assertThat(ticket("new").closed()).isFalse();
        assertThat(ticket(null).closed()).isFalse();
    }

    @Test
    void adrAvecSonStatut() {
        KnowledgeItem adr = KnowledgeItem.adr(PROJECT, "http://doc/adr-002.html", 0, "ADR-002 : synchrone", "corps",
                AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-005"), NOW, NOW);
        assertThat(adr.kind()).isEqualTo(KnowledgeKind.ADR);
        assertThat(adr.status()).isEqualTo("SUPERSEDED");
        assertThat(adr.supersededBy()).isEqualTo("ADR-005");
        assertThat(adr.adrStatus()).isEqualTo(AdrStatus.SUPERSEDED);
    }

    /** Convention adr-tools : successeur cité par son seul numéro. */
    @Test
    void successeurCiteParSonNumero() {
        assertThat(AdrStatus.parse("Statut : Remplacé par 0005"))
                .isEqualTo(AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-0005"));
        assertThat(AdrStatus.parse("Status: Superseded by [5. Trois domaines](0005-trois-domaines.md)"))
                .isEqualTo(AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-5"));
    }

    @Test
    void statutDAdrLuDansLaPage() {
        assertThat(AdrStatus.parse("Statut : Accepté")).isEqualTo(AdrStatus.Statement.of(AdrStatus.ACCEPTED, null));
        assertThat(AdrStatus.parse("Status: Accepted")).isEqualTo(AdrStatus.Statement.of(AdrStatus.ACCEPTED, null));
        assertThat(AdrStatus.parse("Statut : Proposé")).isEqualTo(AdrStatus.Statement.of(AdrStatus.PROPOSED, null));
        assertThat(AdrStatus.parse("Statut : Remplacé par ADR-005"))
                .isEqualTo(AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-005"));
        assertThat(AdrStatus.parse("Status: Superseded by adr-12"))
                .isEqualTo(AdrStatus.Statement.of(AdrStatus.SUPERSEDED, "ADR-12"));
        assertThat(AdrStatus.parse("Statut : Obsolète")).isEqualTo(AdrStatus.Statement.of(AdrStatus.DEPRECATED, null));
        assertThat(AdrStatus.parse("Status: Deprecated")).isEqualTo(AdrStatus.Statement.of(AdrStatus.DEPRECATED, null));
        assertThat(AdrStatus.parse("Statut : en discussion")).isEqualTo(AdrStatus.Statement.UNKNOWN);
        assertThat(AdrStatus.parse(null)).isEqualTo(AdrStatus.Statement.UNKNOWN);
    }

    @Test
    void elementDArchitecture() {
        ArchElement avoirs = new ArchElement("Facturation/Service Avoirs", ArchElement.Type.CONTAINER, ArchScope.PROJECT,
                false, "Calcule et émet les avoirs", "Quarkus");
        KnowledgeItem item = KnowledgeItem.archElement(PROJECT, avoirs, NOW, NOW);
        assertThat(item.kind()).isEqualTo(KnowledgeKind.ARCH_ELEMENT);
        assertThat(item.ref()).isEqualTo("Facturation/Service Avoirs");
        assertThat(item.title()).isEqualTo("Service Avoirs");
        assertThat(item.body()).contains("Calcule et émet les avoirs").contains("Quarkus");
        assertThat(item.elementType()).isEqualTo("CONTAINER");
        assertThat(item.scope()).isEqualTo(ArchScope.PROJECT);
        assertThat(item.transport()).isFalse();
    }
}
