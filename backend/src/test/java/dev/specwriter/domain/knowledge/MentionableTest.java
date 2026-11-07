package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.support.TestMentionables;
import org.junit.jupiter.api.Test;

/** 007, research.md R2 : noms acceptés après @, par sorte de document. */
class MentionableTest {

    @Test
    void documentParNomDeFichierEtChemin() {
        assertThat(TestMentionables.MATRICE.names()).contains("Aurora_Matrice_Regles_Gestion_Activites_1.xlsx",
                "Aurora_Matrice_Regles_Gestion_Activites_1",
                "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx",
                "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1");
        assertThat(TestMentionables.MATRICE.name()).isEqualTo("Aurora_Matrice_Regles_Gestion_Activites_1");
    }

    @Test
    void pageParTitreFichierEtChemin() {
        assertThat(TestMentionables.ARCHITECTURE.names()).contains("Architecture technique du MVP", "architecture.html",
                "architecture", "aurora/architecture.html", "aurora/architecture");
        assertThat(TestMentionables.ARCHITECTURE.name()).isEqualTo("Architecture technique du MVP");
    }

    @Test
    void adrParTitreNumeroEtFichier() {
        assertThat(TestMentionables.ADR_12.names()).contains("12. Sobriété d'usage de l'IA", "ADR-12", "ADR 12", "ADR12",
                "0012-sobriete-usage-ia", "0012-sobriete-usage-ia.html");
        assertThat(TestMentionables.ADR_12.name()).isEqualTo("ADR-12");
    }

    @Test
    void comparaisonSansCasseNiAccents() {
        assertThat(Mentionable.key("Schéma DIRECTEUR")).isEqualTo("schema directeur");
        assertThat(Mentionable.key("Œuvre")).hasSize(5);
    }

    @Test
    void sorteEtTitreDeduitsDeLIndex() {
        Mentionable doc = Mentionable.of(KnowledgeKind.DOC_SECTION, TestMentionables.SOURCE + "Specs/Aurora.docx",
                "Aurora — Contexte", 10);
        assertThat(doc.kind()).isEqualTo(MentionKind.DOCUMENT);
        assertThat(doc.title()).isEqualTo("Aurora");
        assertThat(doc.path()).isEqualTo("Specs/Aurora.docx");
        Mentionable page = Mentionable.of(KnowledgeKind.DOC_SECTION, "aurora/architecture.html", "Architecture", 10);
        assertThat(page.kind()).isEqualTo(MentionKind.PAGE);
        Mentionable adr = Mentionable.of(KnowledgeKind.ADR, TestMentionables.SOURCE + "Decisions/0017-ihm.docx",
                "17. IHM", -3);
        assertThat(adr.kind()).isEqualTo(MentionKind.ADR);
        assertThat(adr.path()).isEqualTo("Decisions/0017-ihm.docx");
        assertThat(adr.size()).isZero();
    }
}
