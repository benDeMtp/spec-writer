package fr.kawamind.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.settings.ManageBrowser;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.BrowserPort;
import fr.kawamind.domain.settings.Browser;
import fr.kawamind.domain.port.LocalFiles;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.infrastructure.platform.LocalFileLocator;
import fr.kawamind.support.InMemoryDocSources;
import fr.kawamind.support.InMemorySettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ouvrir sur le poste le document d'une source citée sans adresse web (site Antora en dossier, OneDrive). */
class OpenSourceDocumentTest {

    static final UUID P = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    static final UUID SITE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID DRIVE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    /** Poste simulé : ce qui a été ouvert, et par qui. */
    static final class Desk implements BrowserPort {
        final List<String> opened = new ArrayList<>();
        boolean fails;

        @Override
        public List<Browser> installed() {
            return List.of(new Browser("chrome", "Google Chrome"));
        }

        @Override
        public boolean open(String browserId, String url) {
            opened.add((browserId == null ? "système" : browserId) + " " + url);
            return !fails;
        }
    }

    @TempDir
    Path tmp;
    Path site;
    Path drive;
    final InMemoryDocSources sources = new InMemoryDocSources();
    final InMemorySettings settings = new InMemorySettings();
    final Desk desk = new Desk();
    final LocalFiles files = new LocalFileLocator();
    OpenSourceDocument open;

    @BeforeEach
    void setUp() throws Exception {
        site = Files.createDirectories(tmp.resolve("site"));
        Files.createDirectories(site.resolve("aurora"));
        Files.writeString(site.resolve("aurora/architecture.html"), "<h1>Architecture</h1>");
        drive = Files.createDirectories(tmp.resolve("OneDrive/General"));
        Files.createDirectories(drive.resolve("10. MVP"));
        Files.writeString(drive.resolve("10. MVP/CR atelier.docx"), "docx");
        Files.writeString(tmp.resolve("secret.txt"), "hors du dossier");
        sources.save(DocSource.create(SITE, P, DocSource.Kind.FOLDER, site.toString()));
        sources.save(DocSource.create(DRIVE, P, DocSource.Kind.DOCUMENTS, drive.toString()));
        open = new OpenSourceDocument(sources, files, new ManageBrowser(settings, desk), desk);
    }

    @Test
    void unePageDuSiteEnDossierSOuvreDansLeNavigateurASaSection() {
        assertThat(open.run(P, "aurora/architecture.html#_aurora_ui")).isEqualTo(OpenSourceDocument.Opened.BROWSER);

        assertThat(desk.opened).singleElement().asString()
                .startsWith("système file:").endsWith("/site/aurora/architecture.html#_aurora_ui");
    }

    @Test
    void leNavigateurChoisiOuvreLaPage() {
        settings.saveBrowser("chrome");

        open.run(P, "aurora/architecture.html");

        assertThat(desk.opened).singleElement().asString().startsWith("chrome file:");
    }

    @Test
    void unDocumentOneDriveSOuvreDansSonApplicationSansSonNumeroDeSection() {
        settings.saveBrowser("chrome");

        assertThat(open.run(P, "docs:" + DRIVE + "/10. MVP/CR atelier.docx#10"))
                .isEqualTo(OpenSourceDocument.Opened.APPLICATION);

        // l'application par défaut du système, jamais le navigateur choisi
        assertThat(desk.opened).singleElement().asString().startsWith("système file:")
                .endsWith("/CR%20atelier.docx");
    }

    @Test
    void rienHorsDesDossiersDeclaresDuProjet() {
        sources.save(DocSource.create(UUID.randomUUID(), OTHER, DocSource.Kind.DOCUMENTS, tmp.toString()));
        for (String ref : List.of("../secret.txt", "docs:" + DRIVE + "/../../secret.txt", "aurora/absente.html",
                "docs:" + UUID.randomUUID() + "/10. MVP/CR atelier.docx", "docs:pas-un-id/x.docx",
                // la source d'un autre projet ne s'ouvre pas depuis celui-ci
                "docs:" + sources.list(OTHER).getFirst().id() + "/secret.txt", tmp.resolve("secret.txt").toString())) {
            assertThatThrownBy(() -> open.run(P, ref)).as(ref).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        }
        assertThat(desk.opened).isEmpty();
    }

    @Test
    void unLienSymboliqueNeFaitPasSortirDuDossier() throws Exception {
        Files.createSymbolicLink(site.resolve("aurora/lien.html"), tmp.resolve("secret.txt"));
        Files.createSymbolicLink(site.resolve("dehors"), tmp);

        for (String ref : List.of("aurora/lien.html", "dehors/secret.txt")) {
            assertThatThrownBy(() -> open.run(P, ref)).as(ref).isInstanceOf(DomainException.class);
        }
        assertThat(desk.opened).isEmpty();
    }

    @Test
    void uneAdresseWebOuVideNEstPasUnFichierDuPoste() {
        for (String ref : new String[] {"https://wiki/pages/viewpage.action?pageId=1", "file:///etc/hosts", "", null}) {
            assertThat(OpenSourceDocument.local(ref)).as(String.valueOf(ref)).isFalse();
            assertThatThrownBy(() -> open.run(P, ref)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(OpenSourceDocument.local("aurora/index.html#_x")).isTrue();
    }

    @Test
    void unEchecDuSystemeEstDit() {
        desk.fails = true;

        assertThatThrownBy(() -> open.run(P, "docs:" + DRIVE + "/10. MVP/CR atelier.docx#1"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("pas pu ouvrir"));
    }
}
