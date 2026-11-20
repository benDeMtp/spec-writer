package fr.kawamind.domain.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueSort.Direction;
import fr.kawamind.domain.jira.IssueSort.Field;
import org.junit.jupiter.api.Test;

/** T009 : tri de la vue Jira (017, research.md R1). */
class IssueSortTest {

    @Test
    void parDefautLesPlusRecemmentModifiesDabord() {
        assertThat(IssueSort.parse(null, null)).isEqualTo(new IssueSort(Field.UPDATED, Direction.DESC));
        assertThat(IssueSort.DEFAULT).isEqualTo(new IssueSort(Field.UPDATED, Direction.DESC));
    }

    @Test
    void lesTroisColonnesDansLesDeuxSens() {
        assertThat(IssueSort.parse("status", "asc")).isEqualTo(new IssueSort(Field.STATUS, Direction.ASC));
        assertThat(IssueSort.parse("sync", "desc")).isEqualTo(new IssueSort(Field.SYNC, Direction.DESC));
        assertThat(IssueSort.parse("updated", "asc")).isEqualTo(new IssueSort(Field.UPDATED, Direction.ASC));
        assertThat(IssueSort.parse("sync", null).direction()).isEqualTo(Direction.DESC);
    }

    @Test
    void toutAutreTriEstRefuse() {
        assertThatThrownBy(() -> IssueSort.parse("title", "asc")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> IssueSort.parse("status", "up")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> IssueSort.parse("status; drop table", "asc")).isInstanceOf(DomainException.class);
    }

    @Test
    void rangsDeStatutParCategorie() {
        assertThat(IssueSort.statusRank("new")).isZero();
        assertThat(IssueSort.statusRank("indeterminate")).isEqualTo(1);
        assertThat(IssueSort.statusRank("done")).isEqualTo(2);
        assertThat(IssueSort.statusRank(null)).isEqualTo(3);
        assertThat(IssueSort.statusRank("undefined")).isEqualTo(3);
    }

    @Test
    void rangsDeSynchroDuPlusUrgentAuMoinsUrgent() {
        assertThat(IssueSort.syncRank(ListedIssue.Sync.CONFLICT)).isZero();
        assertThat(IssueSort.syncRank(ListedIssue.Sync.LOCAL_AHEAD)).isEqualTo(1);
        assertThat(IssueSort.syncRank(ListedIssue.Sync.IN_SYNC)).isEqualTo(2);
        assertThat(IssueSort.syncRank(ListedIssue.Sync.UNLINKED)).isEqualTo(3);
    }

    @Test
    void etatDeSynchroDepuisLeLien() {
        assertThat(ListedIssue.Sync.of(null)).isEqualTo(ListedIssue.Sync.UNLINKED);
        assertThat(ListedIssue.Sync.of(LinkedJiraTicket.SyncState.CONFLICT)).isEqualTo(ListedIssue.Sync.CONFLICT);
        assertThat(ListedIssue.Sync.of(LinkedJiraTicket.SyncState.IN_SYNC)).isEqualTo(ListedIssue.Sync.IN_SYNC);
        assertThat(ListedIssue.Sync.of(LinkedJiraTicket.SyncState.LOCAL_AHEAD)).isEqualTo(ListedIssue.Sync.LOCAL_AHEAD);
    }
}
