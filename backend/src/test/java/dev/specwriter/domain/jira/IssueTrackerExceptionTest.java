package dev.specwriter.domain.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssueTrackerExceptionTest {

    @Test
    void codesPresentesALUtilisateur() {
        assertThat(new IssueTrackerException(Kind.UNAUTHORIZED, "x").errorCode()).isEqualTo(ErrorCode.JIRA_TOKEN_REJECTED);
        assertThat(new IssueTrackerException(Kind.FORBIDDEN, "x").errorCode()).isEqualTo(ErrorCode.JIRA_PERMISSION_DENIED);
        assertThat(new IssueTrackerException(Kind.NOT_FOUND, "x").errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(new IssueTrackerException(Kind.REJECTED, "x").errorCode()).isEqualTo(ErrorCode.JIRA_REJECTED);
        assertThat(new IssueTrackerException(Kind.UNREACHABLE, "x").errorCode()).isEqualTo(ErrorCode.JIRA_UNREACHABLE);
        assertThat(new IssueTrackerException(Kind.RATE_LIMITED, "x").errorCode()).isEqualTo(ErrorCode.JIRA_UNREACHABLE);
    }

    @Test
    void pageDeRechercheImmuable() {
        List<JiraIssueSummary> issues = new ArrayList<>();
        issues.add(new JiraIssueSummary("PROJ-1", "s", "À faire", "Story", Instant.EPOCH));
        JiraSearchPage page = new JiraSearchPage(issues, 0, 1);
        issues.clear();
        assertThat(page.issues()).hasSize(1);
        assertThatThrownBy(() -> page.issues().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
