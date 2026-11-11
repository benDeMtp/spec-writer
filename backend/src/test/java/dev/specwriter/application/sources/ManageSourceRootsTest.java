package dev.specwriter.application.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.SourceRoot;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySourceRoots;
import dev.specwriter.support.SequentialIdGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManageSourceRootsTest {

    @TempDir
    Path dir;

    final InMemoryProjects projects = new InMemoryProjects();
    final InMemorySourceRoots roots = new InMemorySourceRoots();
    final List<SourceRoot> previewed = new ArrayList<>();
    SourceTree.Preview answer = new SourceTree.Preview(List.of(), 3, 1, 0, 0, false, null);
    final SourceTree tree = new SourceTree() {
        @Override
        public Preview preview(SourceRoot root, Limits limits) {
            previewed.add(root);
            return answer;
        }

        @Override
        public Scan search(List<SourceRoot> roots, dev.specwriter.domain.sources.PerimeterTerms terms, Limits limits,
                java.util.function.BooleanSupplier cancelled) {
            throw new UnsupportedOperationException();
        }
    };
    final ManageSourceRoots manage = new ManageSourceRoots(projects, roots, tree, new SequentialIdGenerator(),
            new FixedClock());
    final UUID project = Project.DEFAULT_ID;

    Path folder(String name) throws Exception {
        return Files.createDirectories(dir.resolve(name));
    }

    @Test
    void ajouteUneRacineAvecSesMotifsSansIndexer() throws Exception {
        SourceRoot rv = manage.add(project, folder("rv").toString(), List.of("legacy/**"));
        assertThat(roots.list(project)).containsExactly(rv);
        assertThat(rv.exclusions().patterns()).containsExactly("legacy/**");
        assertThat(manage.list(project)).containsExactly(rv);
    }

    @Test
    void refuseUnProjetInconnuUneRacineEnDoubleOuImbriquee() throws Exception {
        assertThatThrownBy(() -> manage.add(UUID.randomUUID(), folder("rv").toString(), List.of()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        manage.add(project, folder("rv").toString(), List.of());
        assertThatThrownBy(() -> manage.add(project, folder("rv").toString(), List.of()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> manage.add(project, folder("rv/modules").toString(), List.of()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void refuseUneOnziemeRacine() throws Exception {
        for (int i = 0; i < 10; i++) {
            manage.add(project, folder("r" + i).toString(), List.of());
        }
        assertThatThrownBy(() -> manage.add(project, folder("r10").toString(), List.of()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void unMotifInvalideEstRefuseAvecLeMotif() throws Exception {
        assertThatThrownBy(() -> manage.add(project, folder("rv").toString(), List.of("../ailleurs")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("../ailleurs"));
    }

    @Test
    void lesExclusionsSeModifientEtUneRacineSeSupprime() throws Exception {
        SourceRoot rv = manage.add(project, folder("rv").toString(), List.of());
        SourceRoot changed = manage.setExclusions(project, rv.id(), List.of("*.sql"));
        assertThat(roots.find(project, rv.id())).contains(changed);
        manage.delete(project, rv.id());
        assertThat(roots.list(project)).isEmpty();
        assertThatThrownBy(() -> manage.delete(project, rv.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void lApercuParcourtLeDossierSansLeDeclarer() throws Exception {
        answer = new SourceTree.Preview(List.of(new Module(UUID.randomUUID(), "a", "a", ModuleKind.MAVEN, null)), 3, 1, 2,
                0, false, null);
        SourceTree.Preview preview = manage.preview(project, folder("rv").toString(), List.of("legacy/**"));
        assertThat(preview.modules()).hasSize(1);
        assertThat(previewed.getFirst().exclusions().patterns()).containsExactly("legacy/**");
        assertThat(roots.list(project)).isEmpty();
    }

    @Test
    void unDossierIllisibleALApercuEstRefuse() throws Exception {
        answer = SourceTree.Preview.of(SourceTree.RootProblem.ACCESS_DENIED);
        assertThatThrownBy(() -> manage.preview(project, folder("rv").toString(), List.of()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("ne peut pas être lu"));
    }
}
