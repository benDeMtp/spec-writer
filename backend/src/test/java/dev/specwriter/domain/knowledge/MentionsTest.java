package dev.specwriter.domain.knowledge;

import static dev.specwriter.support.TestMentionables.ADR_12;
import static dev.specwriter.support.TestMentionables.ARCHITECTURE;
import static dev.specwriter.support.TestMentionables.MATRICE;
import static dev.specwriter.support.TestMentionables.SCHEMA;
import static dev.specwriter.support.TestMentionables.SCHEMA_COURT;
import static dev.specwriter.support.TestMentionables.SUIVI_A;
import static dev.specwriter.support.TestMentionables.SUIVI_B;
import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.support.TestMentionables;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 007, research.md R1 : grammaire des mentions et plus long nom connu. */
class MentionsTest {

    static final List<Mentionable> CATALOGUE = TestMentionables.all();

    static List<Mention> find(String text) {
        return Mentions.find(text, MentionField.ANSWER, CATALOGUE);
    }

    @Test
    void casReelEcritALaMain() {
        String text = "les information sont disponible dans le document @Aurora_Matrice_Regles_Gestion_Activites_1";
        List<Mention> found = find(text);
        assertThat(found).singleElement().satisfies(m -> {
            assertThat(m.text()).isEqualTo("@Aurora_Matrice_Regles_Gestion_Activites_1");
            assertThat(m.field()).isEqualTo(MentionField.ANSWER);
            assertThat(m.matches()).containsExactly(MATRICE);
            assertThat(text.substring(m.start(), m.end())).isEqualTo(m.text());
            assertThat(m.resolved()).isTrue();
        });
    }

    @Test
    void casseExtensionEtCheminSontAcceptes() {
        assertThat(find("voir @aurora_matrice_regles_gestion_activites_1.XLSX.")).singleElement()
                .satisfies(m -> assertThat(m.matches()).containsExactly(MATRICE));
        assertThat(find("@10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1")).singleElement()
                .satisfies(m -> assertThat(m.matches()).containsExactly(MATRICE));
        assertThat(find("selon (@ADR-12), et @adr 12")).hasSize(2)
                .allSatisfy(m -> assertThat(m.matches()).containsExactly(ADR_12));
        assertThat(find("la page @architecture technique du mvp ou @aurora/architecture")).hasSize(2)
                .allSatisfy(m -> assertThat(m.matches()).containsExactly(ARCHITECTURE));
    }

    @Test
    void plusLongNomConnuAvecEspaces() {
        List<Mention> found = find("lire @Schéma directeur Outils Commerciaux_CoStrat du 26.09_V1 puis @Schéma.");
        assertThat(found).extracting(Mention::text).containsExactly(
                "@Schéma directeur Outils Commerciaux_CoStrat du 26.09_V1", "@Schéma");
        assertThat(found.get(0).matches()).containsExactly(SCHEMA);
        assertThat(found.get(1).matches()).containsExactly(SCHEMA_COURT);
        assertThat(find("@schema directeur outils commerciaux_costrat du 26.09_v1")).singleElement()
                .satisfies(m -> assertThat(m.matches()).containsExactly(SCHEMA));
    }

    @Test
    void ponctuationOuvranteEtFermante() {
        assertThat(find("«@Suivi» [@ADR-12] \"@Schéma\" '@ADR12'")).extracting(Mention::text)
                .containsExactly("@Suivi", "@ADR-12", "@Schéma", "@ADR12");
        // un nom connu doit être suivi d'une fin de mot
        assertThat(find("@Schémas")).singleElement().satisfies(m -> assertThat(m.matches()).isEmpty());
    }

    @Test
    void adressesEtArobasesIsolesIgnores() {
        assertThat(find("écrire à jean@laposte.fr, réunion @ 10 h, fin @")).isEmpty();
    }

    @Test
    void ambigueEtIntrouvable() {
        assertThat(find("@Suivi")).singleElement().satisfies(m -> {
            assertThat(m.matches()).containsExactly(SUIVI_A, SUIVI_B);
            assertThat(m.ambiguous()).isTrue();
        });
        assertThat(find("@Inconnu, merci")).singleElement().satisfies(m -> {
            assertThat(m.text()).isEqualTo("@Inconnu");
            assertThat(m.notFound()).isTrue();
        });
        assertThat(find("@Delivery/Suivi")).singleElement()
                .satisfies(m -> assertThat(m.matches()).containsExactly(SUIVI_A));
    }

    @Test
    void designesDansLOrdreSansDoublon() {
        List<Mentionable> designated = Mentions.designated("Besoin : voir @ADR-12 et @Suivi.",
                List.of("@Aurora_Matrice_Regles_Gestion_Activites_1", "encore @adr-12 et @Inconnu"),
                "ajoute @architecture", CATALOGUE);
        assertThat(designated).containsExactly(ADR_12, MATRICE, ARCHITECTURE);
        assertThat(Mentions.designated(null, List.of(), null, CATALOGUE)).isEmpty();
        assertThat(Mentions.designated("sans mention", List.of(), "", List.of())).isEmpty();
    }

    @Test
    void reecritureSansAmbiguite() {
        assertThat(Mentions.rewrite(SUIVI_B)).isEqualTo("@Archives/Suivi.xlsx");
        assertThat(find(Mentions.rewrite(SUIVI_B))).singleElement()
                .satisfies(m -> assertThat(m.matches()).containsExactly(SUIVI_B));
    }

    @Test
    void sansCatalogueToutEstIntrouvable() {
        assertThat(Mentions.find("@Suivi", MentionField.NEED, List.of())).singleElement()
                .satisfies(m -> assertThat(m.notFound()).isTrue());
        assertThat(Mentions.find(null, MentionField.NEED, CATALOGUE)).isEmpty();
    }
}
