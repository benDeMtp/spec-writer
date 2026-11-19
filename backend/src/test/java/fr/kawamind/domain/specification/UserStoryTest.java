package fr.kawamind.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T065 (015, R9) : énoncé de story et découpage proposé, bornes. */
class UserStoryTest {

    @Test
    void partiesNettoyeesEtElementsSupposes() {
        UserStory story = new UserStory("  gestionnaire de parking ", "réserver une place", "venir sans chercher",
                Set.of(StoryPart.BENEFIT));
        assertThat(story.role()).isEqualTo("gestionnaire de parking");
        assertThat(story.isAssumed(StoryPart.BENEFIT)).isTrue();
        assertThat(story.isAssumed(StoryPart.ROLE)).isFalse();
        assertThat(new UserStory("a", "b", "c", null).assumed()).isEmpty();
    }

    @Test
    void chaquePartieFaitDe1A300Caracteres() {
        String tooLong = "x".repeat(UserStory.MAX_PART + 1);
        assertThat(new UserStory("x".repeat(300), "b", "c", Set.of()).role()).hasSize(300);
        for (String bad : new String[] {null, " ", tooLong}) {
            assertThatThrownBy(() -> new UserStory(bad, "b", "c", Set.of())).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> new UserStory("a", bad, "c", Set.of())).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> new UserStory("a", "b", bad, Set.of())).isInstanceOf(DomainException.class);
        }
    }

    @Test
    void decoupageVideOuDe2A5Titres() {
        assertThat(UserStory.split(null)).isEmpty();
        assertThat(UserStory.split(List.of())).isEmpty();
        assertThat(UserStory.split(List.of(" Réserver ", "Annuler"))).containsExactly("Réserver", "Annuler");
        assertThat(UserStory.split(List.of("1", "2", "3", "4", "5"))).hasSize(5);
        assertThatThrownBy(() -> UserStory.split(List.of("seul"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> UserStory.split(List.of("1", "2", "3", "4", "5", "6")))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> UserStory.split(List.of("a", "x".repeat(UserStory.MAX_SPLIT_TITLE + 1))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> UserStory.split(List.of("a", " "))).isInstanceOf(DomainException.class);
    }

    @Test
    void contenuSansStoryNiDecoupage() {
        SpecificationContent c = new SpecificationContent("T", "R", List.of(), List.of(), List.of(), List.of());
        assertThat(c.story()).isNull();
        assertThat(c.splitProposal()).isEmpty();
    }
}
