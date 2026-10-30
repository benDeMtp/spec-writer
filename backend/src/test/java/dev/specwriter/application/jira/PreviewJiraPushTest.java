package dev.specwriter.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import org.junit.jupiter.api.Test;

class PreviewJiraPushTest {

    final JiraWorld w = new JiraWorld();
    final PreviewJiraPush preview = new PreviewJiraPush(w.versions, w.tickets, w.markup);

    @Test
    void descriptionWikiDuTicketLieSansAppelAJira() {
        Session s = w.generatedSession();
        w.link(s, "PROJ-8", "wiki:d");
        PreviewJiraPush.Preview p = preview.run(s.id(), 1);
        assertThat(p.mode()).isEqualTo(PreviewJiraPush.Mode.UPDATE);
        assertThat(p.issueKey()).isEqualTo("PROJ-8");
        assertThat(p.summary()).isEqualTo(w.versions.find(s.id(), 1).orElseThrow().content().title());
        assertThat(p.descriptionWiki()).isEqualTo("wiki:" + w.versions.find(s.id(), 1).orElseThrow().markdown());
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void versionOuTicketAbsent() {
        Session s = w.generatedSession();
        assertThatThrownBy(() -> preview.run(s.id(), 1)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        w.link(s, "PROJ-9", "wiki:d");
        assertThatThrownBy(() -> preview.run(s.id(), 7)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
