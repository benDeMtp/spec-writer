package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.infrastructure.platform.ProxySettings.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** T139 : proxy d'entreprise lu dans les propriétés système Java et les variables d'environnement. */
class ProxyConfigurationTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    private static final URI JIRA = URI.create("https://jira.corp.example/jira");

    @Test
    void sansConfigurationAucunProxy() {
        ProxySettings settings = ProxySettings.from(Map.of(), props());
        assertThat(settings.forUri(JIRA)).isEmpty();
        assertThat(settings.childEnvironment(Map.of("PATH", "/bin"))).isEqualTo(Map.of("PATH", "/bin"));
    }

    @Test
    void proprietesSystemeJavaPrioritaires() {
        ProxySettings settings = ProxySettings.from(Map.of("HTTPS_PROXY", "http://env-proxy:3128"),
                props("https.proxyHost", "sys-proxy", "https.proxyPort", "8080"));
        assertThat(settings.forUri(JIRA)).contains(new Proxy("sys-proxy", 8080, null, null));
    }

    @Test
    void variablesDEnvironnementAvecIdentifiants() {
        ProxySettings settings = ProxySettings.from(Map.of("https_proxy", "http://alice:s%40cret@proxy.corp:3128/"), props());
        Proxy proxy = settings.forUri(JIRA).orElseThrow();
        assertThat(proxy.host()).isEqualTo("proxy.corp");
        assertThat(proxy.port()).isEqualTo(3128);
        assertThat(proxy.user()).isEqualTo("alice");
        assertThat(proxy.password()).isEqualTo("s@cret");
        assertThat(proxy.toString()).doesNotContain("s@cret").doesNotContain("cret");
    }

    @Test
    void portsParDefautEtSchemaHttp() {
        ProxySettings settings = ProxySettings.from(Map.of("HTTP_PROXY", "proxy.corp"), props("https.proxyHost", "p"));
        assertThat(settings.forUri(JIRA)).contains(new Proxy("p", 443, null, null));
        assertThat(settings.forUri(URI.create("http://jira.corp.example"))).contains(new Proxy("proxy.corp", 80, null, null));
    }

    @Test
    void exclusionsNoProxyEtNonProxyHosts() {
        ProxySettings settings = ProxySettings.from(Map.of("HTTPS_PROXY", "http://p:3128", "NO_PROXY", ".intra.example, direct.example:443"),
                props("http.nonProxyHosts", "*.lan|jira.corp.example"));
        assertThat(settings.forUri(URI.create("https://a.intra.example"))).isEmpty();
        assertThat(settings.forUri(URI.create("https://intra.example"))).isEmpty();
        assertThat(settings.forUri(URI.create("https://direct.example"))).isEmpty();
        assertThat(settings.forUri(URI.create("https://srv.lan"))).isEmpty();
        assertThat(settings.forUri(JIRA)).isEmpty();
        assertThat(settings.forUri(URI.create("https://autre.example"))).isPresent();
        assertThat(settings.forUri(URI.create("https://notintra.example"))).isPresent();
    }

    @Test
    void etoileDesactiveLeProxyEtLaBoucleLocaleNePasseJamaisParLuiAucun() {
        assertThat(ProxySettings.from(Map.of("HTTPS_PROXY", "http://p:3128", "NO_PROXY", "*"), props()).forUri(JIRA)).isEmpty();
        ProxySettings settings = ProxySettings.from(Map.of("HTTPS_PROXY", "http://p:3128"), props());
        assertThat(settings.forUri(URI.create("http://127.0.0.1:47698"))).isEmpty();
        assertThat(settings.forUri(URI.create("http://localhost:8080"))).isEmpty();
        assertThat(settings.forUri(URI.create("http://[::1]:8080"))).isEmpty();
    }

    @Test
    void adresseDeProxyInvalideIgnoree() {
        assertThat(ProxySettings.from(Map.of("HTTPS_PROXY", "http://:abc"), props("https.proxyPort", "x")).forUri(JIRA)).isEmpty();
    }

    @Test
    void environnementDuCopilotCliCompleteParLesProprietesJava() {
        ProxySettings settings = ProxySettings.from(Map.of(),
                props("https.proxyHost", "p", "https.proxyPort", "3128", "http.nonProxyHosts", "*.lan|localhost"));
        Map<String, String> env = settings.childEnvironment(Map.of("PATH", "/bin"));
        assertThat(env).containsEntry("PATH", "/bin").containsEntry("HTTPS_PROXY", "http://p:3128")
                .containsEntry("NO_PROXY", ".lan,localhost");
        // une variable déjà présente n'est jamais remplacée
        assertThat(settings.childEnvironment(Map.of("HTTPS_PROXY", "http://autre:1"))).containsEntry("HTTPS_PROXY", "http://autre:1");
    }
}
