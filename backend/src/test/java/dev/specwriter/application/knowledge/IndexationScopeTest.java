package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 005, US2 : sources choisies d'une réindexation (research.md R2). */
class IndexationScopeTest {

    static final UUID P = Project.DEFAULT_ID;
    final DocSource site = new DocSource(UUID.randomUUID(), P, DocSource.Kind.URL, "https://doc.example.com/", null, null);
    final DocSource folder = new DocSource(UUID.randomUUID(), P, DocSource.Kind.FOLDER, "/docs", null, null);

    @Test
    void toutesLesSources() {
        assertThat(IndexationScope.ALL.all()).isTrue();
        assertThat(IndexationScope.ALL.jira()).isTrue();
        assertThat(IndexationScope.ALL.architecture()).isTrue();
        assertThat(IndexationScope.ALL.includes(site)).isTrue();
    }

    @Test
    void sourcesChoisies() {
        IndexationScope scope = IndexationScope.of(List.of(folder.id().toString()), List.of(site, folder));
        assertThat(scope.all()).isFalse();
        assertThat(scope.jira()).isFalse();
        assertThat(scope.architecture()).isFalse();
        assertThat(scope.includes(folder)).isTrue();
        assertThat(scope.includes(site)).isFalse();

        IndexationScope other = IndexationScope.of(List.of("JIRA", "ARCHITECTURE"), List.of(site));
        assertThat(other.jira()).isTrue();
        assertThat(other.architecture()).isTrue();
        assertThat(other.includes(site)).isFalse();
        assertThat(other.all()).isFalse();
    }

    @Test
    void toutCocherResteUnChoix() {
        // toutes les sources cochées : toutes relues, mais la date « Synchronisée le » ne change pas
        IndexationScope scope = IndexationScope.of(List.of("JIRA", "ARCHITECTURE", site.id().toString()), List.of(site));
        assertThat(scope.includes(site)).isTrue();
        assertThat(scope.all()).isFalse();
    }

    @Test
    void choixRefuses() {
        assertThatThrownBy(() -> IndexationScope.of(List.of(), List.of(site))).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> IndexationScope.of(List.of("WIKI"), List.of(site))).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        // une source de documentation d'un autre projet n'est pas dans la liste déclarée
        assertThatThrownBy(() -> IndexationScope.of(List.of(UUID.randomUUID().toString()), List.of(site)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> IndexationScope.of(java.util.Arrays.asList((String) null), List.of(site)))
                .isInstanceOf(DomainException.class);
    }
}
