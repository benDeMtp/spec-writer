package dev.specwriter.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fiche générale et sources de documentation d'un projet (FR-008, FR-013 de 002). */
class ContextUseCasesTest {

    static final UUID P = Project.DEFAULT_ID;
    final JiraWorld w = new JiraWorld();
    final EditContext edit = new EditContext(w.projects, w.clock);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final ManageDocSources sources = new ManageDocSources(w.projects, w.docSources, w.ids, index, w.folder);

    @Test
    void ficheGeneraleEnregistreeEtDatee() {
        Project edited = edit.run(P, "## Acteurs\n- Le gestionnaire.");
        assertThat(edited.context().markdown()).isEqualTo("## Acteurs\n- Le gestionnaire.");
        assertThat(edited.context().updatedAt()).isEqualTo(w.clock.now());
        assertThat(w.projects.find(P).orElseThrow().context().markdown()).isEqualTo("## Acteurs\n- Le gestionnaire.");
        assertThatThrownBy(() -> edit.run(UUID.randomUUID(), "x")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    /** 006 : dossier de documents avec ses motifs ; le supprimer retire ses éléments, et eux seuls. */
    @Test
    void dossierDeDocumentsAjouteEtSupprime(@TempDir Path folder) {
        DocSource docs = sources.add(P, DocSource.Kind.DOCUMENTS, folder.toString(), List.of("Archives/**", " "));
        assertThat(docs.exclusions().patterns()).containsExactly("Archives/**");
        Instant t = w.clock.now();
        index.save(KnowledgeItem.docSection(P, DocumentFolder.prefix(docs) + "a.docx#0", 0, "A", "a", null, t, t), null, null);
        index.save(KnowledgeItem.docSection(P, "guide.html#_a", 0, "Guide", "g", null, t, t), null, null);

        sources.delete(P, docs.id());

        assertThat(index.all(P, KnowledgeKind.DOC_SECTION)).extracting(KnowledgeItem::ref).containsExactly("guide.html#_a");
        assertThatThrownBy(() -> sources.add(P, DocSource.Kind.FOLDER, folder.toString(), List.of("*.pdf")))
                .isInstanceOf(DomainException.class);
    }

    /** 006, US2 : motifs modifiés, et aperçu sans indexation (10 exemples au plus). */
    @Test
    void motifsEtApercu(@TempDir Path folder) {
        DocSource docs = sources.add(P, DocSource.Kind.DOCUMENTS, folder.toString(), List.of());
        for (int i = 0; i < 12; i++) {
            w.folder.put(folder.toString(), "Specs/doc" + i + ".docx", 1, w.clock.now(), null);
        }
        w.folder.put(folder.toString(), "Archives/2023.pdf", 1, w.clock.now(), null);
        w.folder.ignored.put(folder.toString(), List.of("vieux.doc"));

        ManageDocSources.Preview preview = sources.preview(P, folder.toString(), List.of("Archives/**"));

        assertThat(preview.retained()).isEqualTo(12);
        assertThat(preview.excluded()).isEqualTo(1);
        assertThat(preview.ignored()).isEqualTo(1);
        assertThat(preview.retainedExamples()).hasSize(10).first().isEqualTo("Specs/doc0.docx");
        assertThat(preview.excludedExamples()).containsExactly("Archives/2023.pdf");
        assertThat(preview.ignoredExamples()).containsExactly("vieux.doc");
        assertThat(w.folder.reads).isEmpty();

        DocSource changed = sources.setExclusions(P, docs.id(), List.of("*.pdf"));
        assertThat(changed.exclusions().patterns()).containsExactly("*.pdf");
        assertThat(w.docSources.find(docs.id())).contains(changed);
        assertThatThrownBy(() -> sources.setExclusions(P, docs.id(), List.of("/abs"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> sources.setExclusions(UUID.randomUUID(), docs.id(), List.of()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> sources.preview(P, folder.resolve("absent").toString(), List.of()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void sourcesAjouteesListeesEtSupprimees() {
        DocSource site = sources.add(P, DocSource.Kind.URL, "https://docs.example.com/facturation/");
        assertThat(sources.list(P)).containsExactly(site);
        // les pages indexées de la source partent avec elle, quel que soit son type
        KnowledgeItem page = KnowledgeItem.adr(P, "https://docs.example.com/facturation/adr-004.html", 0, "ADR-004", "x",
                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), Instant.EPOCH, Instant.EPOCH);
        index.save(page, null, null);
        index.replaceSource(P, site.id(), Set.of(KnowledgeIndex.Key.of(page)));
        sources.delete(P, site.id());
        assertThat(sources.list(P)).isEmpty();
        assertThat(index.count(P, KnowledgeKind.ADR)).isZero();

        assertThatThrownBy(() -> sources.delete(P, site.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> sources.list(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void uneSourceDUnAutreProjetNePeutPasEtreSupprimee() {
        DocSource site = sources.add(P, DocSource.Kind.URL, "https://docs.example.com/facturation/");
        UUID other = UUID.randomUUID();
        w.projects.save(Project.create(other, dev.specwriter.domain.project.ProjectName.of("Autre"), null, null,
                w.clock.now()));
        assertThatThrownBy(() -> sources.delete(other, site.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(sources.list(P)).containsExactly(site);
    }

    @Test
    void dixSourcesAuPlus() {
        for (int i = 0; i < 10; i++) {
            sources.add(P, DocSource.Kind.URL, "https://docs.example.com/site" + i + "/");
        }
        assertThatThrownBy(() -> sources.add(P, DocSource.Kind.URL, "https://docs.example.com/site10/"))
                .isInstanceOf(DomainException.class);
        assertThat(sources.list(P)).hasSize(10);
    }
}
