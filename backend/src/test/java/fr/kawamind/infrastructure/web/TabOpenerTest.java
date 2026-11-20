package fr.kawamind.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.settings.ManageBrowser;
import fr.kawamind.domain.settings.Browser;
import fr.kawamind.support.InMemorySettings;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T011 : ouverture d'un onglet avec un nouveau jeton, pour un second lancement ou le Dock (019, R2). */
class TabOpenerTest {

    final LaunchTokenService tokens = new LaunchTokenService((java.nio.file.Path) null);
    final List<String> opened = new ArrayList<>();
    boolean browserWorks = true;
    final ManageBrowser browser = new ManageBrowser(new InMemorySettings(), new fr.kawamind.domain.port.BrowserPort() {
        @Override
        public List<Browser> installed() {
            return List.of();
        }

        @Override
        public boolean open(String browserId, String url) {
            opened.add(url);
            return browserWorks;
        }
    });

    TabOpener opener(boolean openBrowser) {
        return new TabOpener(tokens, browser, 47615, openBrowser);
    }

    @Test
    void ouvreLeNavigateurAvecUnNouveauJetonValable() {
        TabOpener.Result result = opener(true).open();

        assertThat(result.status()).isEqualTo(TabOpener.Status.OPENED);
        assertThat(result.url()).isNull();
        assertThat(opened).hasSize(1);
        String token = URI.create(opened.getFirst()).getQuery().substring("launch=".length());
        assertThat(token).isNotEqualTo(tokens.launchToken());
        assertThat(tokens.exchange(token)).isPresent();
        assertThat(tokens.exchange(token)).as("usage unique").isEmpty();
    }

    @Test
    void chaqueOuvertureEmetSonPropreJeton() {
        opener(true).open();
        opener(true).open();

        assertThat(opened).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void sansNavigateurLAdresseEstRendue() {
        TabOpener.Result result = opener(false).open();

        assertThat(result.status()).isEqualTo(TabOpener.Status.PRINT);
        assertThat(result.url()).startsWith("http://127.0.0.1:47615/?launch=");
        assertThat(opened).isEmpty();
    }

    @Test
    void siLeNavigateurEchoueLAdresseEstRendue() {
        browserWorks = false;

        TabOpener.Result result = opener(true).open();

        assertThat(result.status()).isEqualTo(TabOpener.Status.PRINT);
        assertThat(result.url()).isNotNull();
    }
}
