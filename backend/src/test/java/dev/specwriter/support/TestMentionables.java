package dev.specwriter.support;

import dev.specwriter.domain.knowledge.MentionKind;
import dev.specwriter.domain.knowledge.Mentionable;
import java.util.List;

/** Catalogue fictif des documents mentionnables (007, T001) et ses pièges (research.md R1, R2). */
public final class TestMentionables {

    public static final String SOURCE = "docs:00000000-0000-0000-0000-000000000006/";
    public static final Mentionable MATRICE = new Mentionable(MentionKind.DOCUMENT,
            SOURCE + "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx",
            "Aurora_Matrice_Regles_Gestion_Activites_1", "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx",
            30_000);
    public static final Mentionable SCHEMA = new Mentionable(MentionKind.DOCUMENT,
            SOURCE + "0. Pilotage/Schéma directeur Outils Commerciaux_CoStrat du 26.09_V1.pdf",
            "Schéma directeur Outils Commerciaux_CoStrat du 26.09_V1",
            "0. Pilotage/Schéma directeur Outils Commerciaux_CoStrat du 26.09_V1.pdf", 5_000);
    /** Préfixe du nom d'un autre document : le plus long nom l'emporte. */
    public static final Mentionable SCHEMA_COURT = new Mentionable(MentionKind.DOCUMENT, SOURCE + "Schéma.docx",
            "Schéma", "Schéma.docx", 1_000);
    public static final Mentionable SUIVI_A = new Mentionable(MentionKind.DOCUMENT, SOURCE + "Delivery/Suivi.xlsx",
            "Suivi", "Delivery/Suivi.xlsx", 2_000);
    public static final Mentionable SUIVI_B = new Mentionable(MentionKind.DOCUMENT, SOURCE + "Archives/Suivi.xlsx",
            "Suivi", "Archives/Suivi.xlsx", 2_000);
    public static final Mentionable ARCHITECTURE = new Mentionable(MentionKind.PAGE, "aurora/architecture.html",
            "Architecture technique du MVP", "aurora/architecture.html", 9_000);
    public static final Mentionable ADR_12 = new Mentionable(MentionKind.ADR, "aurora/adr/0012-sobriete-usage-ia.html",
            "12. Sobriété d'usage de l'IA", "aurora/adr/0012-sobriete-usage-ia.html", 3_000);

    private TestMentionables() {
    }

    public static List<Mentionable> all() {
        return List.of(MATRICE, SCHEMA, SCHEMA_COURT, SUIVI_A, SUIVI_B, ARCHITECTURE, ADR_12);
    }
}
