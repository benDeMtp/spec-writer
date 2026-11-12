package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.settings.Browser;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Détection des navigateurs installés et commande de lancement, par système (013, T057). */
class DesktopBrowsersTest {

    final List<List<String>> launched = new ArrayList<>();
    final List<Boolean> waited = new ArrayList<>();
    final List<URI> defaults = new ArrayList<>();

    DesktopBrowsers desk(String os, Map<String, String> env, Set<Path> present) {
        BrowserLauncher system = new BrowserLauncher(os, new BrowserLauncher.DesktopBrowser() {
            @Override
            public boolean supported() {
                return true;
            }

            @Override
            public void browse(URI uri) {
                defaults.add(uri);
            }
        }, c -> false);
        return new DesktopBrowsers(os, env, Path.of("/Users/moi"), present::contains, (command, wait) -> {
            launched.add(command);
            waited.add(wait);
            return true;
        }, system);
    }

    @Test
    void macOsChercheLesApplicationsEtLesOuvreParOpen() {
        DesktopBrowsers mac = desk("Mac OS X", Map.of(), Set.of(Path.of("/Applications/Safari.app"),
                Path.of("/Applications/Google Chrome.app"), Path.of("/Users/moi/Applications/Firefox.app")));

        assertThat(mac.installed()).extracting(Browser::id).containsExactly("chrome", "firefox", "safari");
        assertThat(mac.open("firefox", "http://127.0.0.1:1/?launch=x")).isTrue();
        assertThat(launched).containsExactly(
                List.of("open", "-a", "/Users/moi/Applications/Firefox.app", "http://127.0.0.1:1/?launch=x"));
        assertThat(waited).containsExactly(true);
        // un navigateur absent ne lance rien
        assertThat(mac.open("edge", "u")).isFalse();
        assertThat(launched).hasSize(1);
    }

    @Test
    void windowsChercheDansLesDossiersDeProgrammes() {
        String x86 = "C:" + File.separator + "Program Files (x86)";
        Path edge = Path.of(x86).resolve("Microsoft/Edge/Application/msedge.exe");
        DesktopBrowsers win = desk("Windows 11", Map.of("ProgramFiles(x86)", x86), Set.of(edge));

        assertThat(win.installed()).extracting(Browser::name).containsExactly("Microsoft Edge");
        assertThat(win.open("edge", "u")).isTrue();
        assertThat(launched).containsExactly(List.of(edge.toString(), "u"));
        assertThat(waited).containsExactly(false);
    }

    @Test
    void linuxChercheLesCommandesDuPath() {
        DesktopBrowsers linux = desk("Linux", Map.of("PATH", "/usr/local/bin" + File.pathSeparator + "/usr/bin"),
                Set.of(Path.of("/usr/bin/firefox"), Path.of("/usr/bin/chromium-browser")));

        assertThat(linux.installed()).extracting(Browser::id).containsExactly("firefox", "chromium");
        assertThat(linux.open("chromium", "u")).isTrue();
        assertThat(launched).containsExactly(List.of("/usr/bin/chromium-browser", "u"));
    }

    @Test
    void sansChoixLeNavigateurDuSysteme() {
        DesktopBrowsers mac = desk("Mac OS X", Map.of(), Set.of());
        assertThat(mac.installed()).isEmpty();
        assertThat(mac.open(null, "http://127.0.0.1:1/")).isTrue();
        assertThat(defaults).containsExactly(URI.create("http://127.0.0.1:1/"));
        assertThat(launched).isEmpty();
    }
}
