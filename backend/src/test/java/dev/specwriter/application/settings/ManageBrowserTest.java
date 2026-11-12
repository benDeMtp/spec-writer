package dev.specwriter.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.port.BrowserPort;
import dev.specwriter.domain.settings.Browser;
import dev.specwriter.support.InMemorySettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Navigateur où s'ouvre spec-writer (013, T057). */
class ManageBrowserTest {

    /** Poste simulé : navigateurs installés, et ceux qui refusent de s'ouvrir. */
    static final class Desk implements BrowserPort {
        List<Browser> installed = new ArrayList<>(List.of(new Browser("chrome", "Google Chrome"),
                new Browser("firefox", "Firefox")));
        final List<String> opened = new ArrayList<>();
        String failing;

        @Override
        public List<Browser> installed() {
            return installed;
        }

        @Override
        public boolean open(String browserId, String url) {
            opened.add(browserId == null ? "système" : browserId);
            return browserId == null || !browserId.equals(failing);
        }
    }

    final InMemorySettings settings = new InMemorySettings();
    final Desk desk = new Desk();
    final ManageBrowser browser = new ManageBrowser(settings, desk);

    @Test
    void parDefautLeNavigateurDuSysteme() {
        ManageBrowser.View view = browser.get();
        assertThat(view.selected()).isNull();
        assertThat(view.missing()).isFalse();
        assertThat(view.installed()).extracting(Browser::name).containsExactly("Google Chrome", "Firefox");

        ManageBrowser.Opened opened = browser.open("http://127.0.0.1:1/?launch=x");
        assertThat(opened.opened()).isTrue();
        assertThat(opened.browser()).isNull();
        assertThat(desk.opened).containsExactly("système");
    }

    @Test
    void leNavigateurChoisiOuvreSpecWriter() {
        assertThat(browser.save(" chrome ").selected()).isEqualTo("chrome");
        ManageBrowser.Opened opened = browser.open("u");
        assertThat(opened.browser().name()).isEqualTo("Google Chrome");
        assertThat(desk.opened).containsExactly("chrome");
        // vide : retour au navigateur du système
        assertThat(browser.save("").selected()).isNull();
        assertThat(settings.browser()).isNull();
    }

    @Test
    void unNavigateurNonInstalleEstRefuse() {
        assertThatThrownBy(() -> browser.save("netscape")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.userMessage()).contains("pas installé"));
        assertThat(settings.browser()).isNull();
    }

    @Test
    void unNavigateurDesinstalleOuEnEchecLaissePlaceAuSysteme() {
        browser.save("firefox");
        desk.failing = "firefox";
        assertThat(browser.open("u").browser()).isNull();
        assertThat(desk.opened).containsExactly("firefox", "système");

        desk.installed.removeIf(b -> b.id().equals("firefox"));
        assertThat(browser.get().missing()).isTrue();
        assertThat(browser.open("u").opened()).isTrue();
        assertThat(desk.opened).containsExactly("firefox", "système", "système");
    }
}
