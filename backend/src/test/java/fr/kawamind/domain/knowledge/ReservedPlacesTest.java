package fr.kawamind.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** T038 (016) : places réservées aux extraits de référence (FR-019, FR-020), sans toucher au classement. */
class ReservedPlacesTest {

    static final Predicate<String> REF = s -> s.startsWith("R");

    static List<String> others(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> "o" + i).toList();
    }

    static List<String> with(List<String> base, String... extra) {
        List<String> out = new java.util.ArrayList<>(base);
        out.addAll(List.of(extra));
        return out;
    }

    @Test
    void aucuneReferenceCandidateListeInchangee() {
        assertThat(ReservedPlaces.apply(others(12), 8, REF)).isEqualTo(others(8));
    }

    @Test
    void niveauxUniformesTousDeReferenceListeInchangee() {
        List<String> all = IntStream.rangeClosed(1, 12).mapToObj(i -> "R" + i).toList();
        assertThat(ReservedPlaces.apply(all, 8, REF)).isEqualTo(all.subList(0, 8));
    }

    @Test
    void lesDeuxMieuxClasseesPrennentLesDernieresPlaces() {
        List<String> ranked = with(others(10), "R1", "R2", "R3");
        assertThat(ReservedPlaces.apply(ranked, 8, REF)).containsExactly("o1", "o2", "o3", "o4", "o5", "o6", "R1",
                "R2");
    }

    @Test
    void uneSeuleCandidateUnePlace() {
        assertThat(ReservedPlaces.apply(with(others(10), "R1"), 8, REF)).containsExactly("o1", "o2", "o3", "o4", "o5",
                "o6", "o7", "R1");
    }

    @Test
    void uneReferenceDejaPresenteUnSeulRemplacement() {
        List<String> ranked = with(List.of("o1", "R1"), "o2", "o3", "o4", "o5", "o6", "o7", "o8", "R2", "R3");
        assertThat(ReservedPlaces.apply(ranked, 8, REF)).containsExactly("o1", "R1", "o2", "o3", "o4", "o5", "o6",
                "R2");
    }

    @Test
    void troisReferencesDejaDansLesHuitInchange() {
        List<String> ranked = List.of("R1", "o1", "R2", "o2", "R3", "o3", "o4", "o5", "o6", "R4");
        assertThat(ReservedPlaces.apply(ranked, 8, REF)).isEqualTo(ranked.subList(0, 8));
    }

    @Test
    void jamaisPlusDeDeuxRemplacementsNiDeDoublon() {
        List<String> ranked = with(others(8), "R1", "R1", "R2", "R3", "R4");
        List<String> out = ReservedPlaces.apply(ranked, 8, REF);
        assertThat(out).hasSize(8).doesNotHaveDuplicates();
        assertThat(out.stream().filter(REF)).hasSize(2);
    }

    @Test
    void moinsDeCandidatsQueDePlaces() {
        assertThat(ReservedPlaces.apply(List.of("o1", "R1"), 8, REF)).containsExactly("o1", "R1");
    }
}
