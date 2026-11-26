package fr.kawamind.domain.jira;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 022 (US1, research R3) : sous-tâches d'un ticket de la vue Jira et ordre des clés. */
class ListedIssueTest {

    static final Instant T = Instant.parse("2026-11-25T09:00:00Z");

    static ListedIssue issue(String key) {
        return new ListedIssue(key, "Titre", "À faire", "new", T, ListedIssue.Sync.UNLINKED, null);
    }

    @Test
    void lesClesSeClassentParPrefixePuisParNumero() {
        List<String> keys = new ArrayList<>(List.of("AURORA-182", "AURORA-25", "AURORA-9", "AB-100", "AURORA-X",
                "SANS"));
        keys.sort(ListedIssue.KEY_ORDER);
        assertThat(keys).containsExactly("AB-100", "AURORA-9", "AURORA-25", "AURORA-182", "AURORA-X", "SANS");
    }

    @Test
    void unTicketSansSousTacheEnAUneListeVide() {
        ListedIssue story = issue("S-1");
        assertThat(story.subtasks()).isEmpty();
        assertThat(story.parentKey()).isNull();
        ListedIssue withChildren = story.withSubtasks(List.of(issue("S-2")));
        assertThat(withChildren.subtasks()).extracting(ListedIssue::key).containsExactly("S-2");
        assertThat(withChildren.key()).isEqualTo("S-1");
        assertThat(new ListedIssue("O-5", "T", null, null, null, ListedIssue.Sync.UNLINKED, null, "P-9", null)
                .subtasks()).isEmpty();
    }
}
