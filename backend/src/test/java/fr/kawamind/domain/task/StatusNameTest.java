package fr.kawamind.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 023 (research R3) : un statut Jira se désigne par son nom, sans tenir compte de la casse ni des accents. */
class StatusNameTest {

    @Test
    void casseAccentsEtEspacesIgnores() {
        assertThat(StatusName.same("En Cours", "en cours")).isTrue();
        assertThat(StatusName.same("Réalisée", "Realisee")).isTrue();
        assertThat(StatusName.same(" Fermée ", "fermée")).isTrue();
        assertThat(StatusName.same("Réalisée", "Réalisé")).isFalse();
        assertThat(StatusName.same(null, "")).isTrue();
        assertThat(StatusName.normalize(null)).isEmpty();
        assertThat(StatusName.normalize("À Valider")).isEqualTo("a valider");
    }
}
