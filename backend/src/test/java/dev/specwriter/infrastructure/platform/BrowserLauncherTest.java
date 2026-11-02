package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserLauncherTest {

    private static final URI URL = URI.create("http://127.0.0.1:47615/?launch=abc");

    private final List<List<String>> commandes = new ArrayList<>();

    private BrowserLauncher launcher(String os, boolean desktop, boolean desktopOk, List<String> commandesEnEchec) {
        BrowserLauncher.DesktopBrowser browser = new BrowserLauncher.DesktopBrowser() {
            @Override
            public boolean supported() {
                return desktop;
            }

            @Override
            public void browse(URI uri) throws Exception {
                if (!desktopOk) {
                    throw new IllegalStateException("échec");
                }
                commandes.add(List.of("desktop", uri.toString()));
            }
        };
        BrowserLauncher.CommandRunner runner = cmd -> {
            commandes.add(cmd);
            return !commandesEnEchec.contains(cmd.get(0));
        };
        return new BrowserLauncher(os, browser, runner);
    }

    @Test
    void desktopEnPremier() {
        assertThat(launcher("Mac OS X", true, true, List.of()).open(URL)).isTrue();
        assertThat(commandes).containsExactly(List.of("desktop", URL.toString()));
    }

    @Test
    void macosRetombeSurOpen() {
        assertThat(launcher("Mac OS X", true, false, List.of()).open(URL)).isTrue();
        assertThat(commandes).last().isEqualTo(List.of("open", URL.toString()));
    }

    @Test
    void linuxRetombeSurXdgOpen() {
        assertThat(launcher("Linux", false, false, List.of()).open(URL)).isTrue();
        assertThat(commandes).containsExactly(List.of("xdg-open", URL.toString()));
    }

    @Test
    void windowsRetombeSurRundll32() {
        assertThat(launcher("Windows 11", false, false, List.of()).open(URL)).isTrue();
        assertThat(commandes).containsExactly(List.of("rundll32", "url.dll,FileProtocolHandler", URL.toString()));
    }

    @Test
    void echecGlobalSignale() {
        assertThat(launcher("Linux", false, false, List.of("xdg-open")).open(URL)).isFalse();
    }
}
