package fr.kawamind.infrastructure.confluence;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluenceClient.ConfluenceException;
import fr.kawamind.domain.port.ConfluenceClient.Endpoint;
import fr.kawamind.domain.port.ConfluenceClient.Failure;
import fr.kawamind.domain.port.DocumentationReader;
import fr.kawamind.infrastructure.jira.TestCertificates;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T021 (016) : client Confluence contre WireMock (contracts/confluence-client.md). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class ConfluenceRestClientIT {

    static WireMockServer wiki;
    static TestCertificates certs;
    static final String BASE = "/confluence/rest/api";

    @Inject
    ConfluenceRestClient client;

    @BeforeAll
    static void start() throws Exception {
        certs = TestCertificates.generate(Path.of("target/test-certs-confluence"));
        wiki = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().dynamicHttpsPort()
                .keystorePath(certs.serverKeystore.toString()).keystorePassword(TestCertificates.PASSWORD)
                .keyManagerPassword(TestCertificates.PASSWORD).keystoreType("PKCS12"));
        wiki.start();
        wiki.stubFor(get(BASE + "/user/current").withHeader("Authorization", equalTo("Bearer PAT-CONF"))
                .willReturn(okJson("{\"username\":\"ada\",\"displayName\":\"Ada Lovelace\"}")));
        wiki.stubFor(get(BASE + "/user/current").withHeader("Authorization", equalTo("Bearer mauvais"))
                .willReturn(aResponse().withStatus(401)));
        wiki.stubFor(get(BASE + "/settings/systemInfo").willReturn(aResponse().withStatus(403)));
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content")).withQueryParam("spaceKey", equalTo("REMB"))
                .withQueryParam("title", equalTo("Remboursements des frais"))
                .willReturn(okJson("{\"results\":[{\"id\":\"100\",\"type\":\"page\"}],\"size\":1}")));
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content/100")).withQueryParam("expand", equalTo("version,space"))
                .willReturn(okJson("""
                        {"id":"100","type":"page","title":"Remboursements",
                         "version":{"number":4,"when":"2026-09-01T09:00:00.000+02:00"},"space":{"key":"REMB"}}""")));
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content/100")).withQueryParam("expand", equalTo("body.view,version"))
                .willReturn(okJson("""
                        {"id":"100","type":"page","title":"Remboursements",
                         "version":{"number":4,"when":"2026-09-01T09:00:00.000+02:00"},
                         "body":{"view":{"value":"<p>Intro.</p><h2>Plafond</h2><p>500 euros.</p><h2>Circuit</h2><p>Manager.</p>"}}}""")));
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content/111")).willReturn(aResponse().withStatus(403)));
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content/404")).willReturn(aResponse().withStatus(404)));
        for (int start : List.of(0, 2)) {
            wiki.stubFor(get(urlPathEqualTo(BASE + "/content/search")).withQueryParam("cql",
                            equalTo("ancestor=100 and type=page")).withQueryParam("start", equalTo(String.valueOf(start)))
                    .willReturn(okJson("{\"results\":[" + (start == 0
                            ? "{\"id\":\"101\",\"title\":\"A\",\"version\":{\"number\":1,\"when\":\"2026-09-03T10:00:00.000+02:00\"}},"
                                    + "{\"id\":\"102\",\"title\":\"B\",\"version\":{\"number\":2}}"
                            : "{\"id\":\"105\",\"title\":\"C\",\"version\":{\"number\":1}}")
                            + "],\"start\":" + start + ",\"size\":" + (start == 0 ? 2 : 1) + ",\"totalSize\":3}")));
        }
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content/429")).inScenario("debit").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")).willSetStateTo("ok"));
        wiki.stubFor(get(urlPathEqualTo(BASE + "/content/429")).inScenario("debit").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"id\":\"429\",\"type\":\"page\",\"title\":\"Enfin\",\"version\":{\"number\":1}}")));
    }

    @AfterAll
    static void stop() {
        wiki.stop();
    }

    static Endpoint http(String token) {
        return new Endpoint("http://127.0.0.1:" + wiki.port() + "/confluence", token, null);
    }

    @Test
    void compteDuJetonEnBearer() {
        assertThat(client.currentUser(http("PAT-CONF"))).isEqualTo("Ada Lovelace");
        assertThat(wiki.findAll(getRequestedFor(urlPathEqualTo(BASE + "/user/current"))))
                .anySatisfy(r -> assertThat(r.getHeader("Authorization")).isEqualTo("Bearer PAT-CONF"));
        assertThatThrownBy(() -> client.currentUser(http("mauvais"))).isInstanceOf(ConfluenceException.class)
                .satisfies(e -> assertThat(((ConfluenceException) e).failure()).isEqualTo(Failure.UNAUTHORIZED))
                .hasMessageNotContaining("mauvais");
        // version facultative, refusée aux comptes ordinaires
        assertThat(client.serverVersion(http("PAT-CONF"))).isEmpty();
    }

    @Test
    void racineParIdentifiantEtParTitre() {
        Endpoint e = http("PAT-CONF");
        String server = e.serverUrl();
        assertThat(client.resolve(e, server + "/pages/viewpage.action?pageId=100")).isEqualTo("100");
        assertThat(client.resolve(e, server + "/display/REMB/Remboursements+des+frais")).isEqualTo("100");
        assertThatThrownBy(() -> client.resolve(e, "https://autre.example.com/confluence/pages/viewpage.action?pageId=1"))
                .satisfies(x -> assertThat(((ConfluenceException) x).failure()).isEqualTo(Failure.OTHER_HOST));
        ConfluenceClient.PageInfo root = client.page(e, "100");
        assertThat(root.title()).isEqualTo("Remboursements");
        assertThat(root.version()).isEqualTo(4);
        assertThat(root.updatedAt()).isEqualTo(Instant.parse("2026-09-01T07:00:00Z"));
    }

    @Test
    void descendancePagineeParAncestor() {
        Endpoint e = http("PAT-CONF");
        ConfluenceClient.Descendants first = client.descendants(e, "100", 0, 100);
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.pages()).extracting(ConfluenceClient.PageInfo::id).containsExactly("101", "102");
        assertThat(client.descendants(e, "100", 2, 100).pages()).extracting(ConfluenceClient.PageInfo::id)
                .containsExactly("105");
    }

    @Test
    void contenuRenduDecoupeEnSections() {
        String ref = ConfluenceClient.pageRef(http("PAT-CONF").serverUrl(), "100");
        DocumentationReader.Page page = client.content(http("PAT-CONF"), "100", ref);
        assertThat(page.updatedAt()).isEqualTo(Instant.parse("2026-09-01T07:00:00Z"));
        assertThat(page.sections()).extracting(DocumentationReader.Section::title)
                .containsExactly("Remboursements", "Remboursements — Plafond", "Remboursements — Circuit");
        assertThat(page.sections()).extracting(DocumentationReader.Section::ref)
                .allMatch(r -> r.startsWith(ref + "#"));
    }

    @Test
    void pageRestreinteEtPageDisparue() {
        assertThatThrownBy(() -> client.content(http("PAT-CONF"), "111", "x"))
                .satisfies(x -> assertThat(((ConfluenceException) x).failure()).isEqualTo(Failure.FORBIDDEN));
        assertThatThrownBy(() -> client.page(http("PAT-CONF"), "404"))
                .satisfies(x -> assertThat(((ConfluenceException) x).failure()).isEqualTo(Failure.NOT_FOUND));
    }

    @Test
    void debitLimiteAttendRetryAfter() {
        long started = System.nanoTime();
        assertThat(client.page(http("PAT-CONF"), "429").title()).isEqualTo("Enfin");
        assertThat(System.nanoTime() - started).isGreaterThanOrEqualTo(900_000_000L);
    }

    @Test
    void auPlusHuitRequetesParSeconde() {
        Endpoint e = http("PAT-CONF");
        wiki.resetRequests();
        for (int i = 0; i < 12; i++) {
            client.descendants(e, "100", 0, 100);
        }
        List<Long> at = wiki.getAllServeEvents().stream().map(ServeEvent::getRequest)
                .map(r -> r.getLoggedDate().getTime()).sorted().toList();
        for (int i = 8; i < at.size(); i++) {
            assertThat(at.get(i) - at.get(i - 8)).isGreaterThanOrEqualTo(1_000L);
        }
    }

    @Test
    void httpsAvecAutoriteInterne() {
        Endpoint e = new Endpoint("https://127.0.0.1:" + wiki.httpsPort() + "/confluence", "PAT-CONF",
                certs.caPem.toString());
        assertThat(client.currentUser(e)).isEqualTo("Ada Lovelace");
        Endpoint sansCa = new Endpoint("https://127.0.0.1:" + wiki.httpsPort() + "/confluence", "PAT-CONF", null);
        assertThatThrownBy(() -> client.currentUser(sansCa))
                .satisfies(x -> assertThat(((ConfluenceException) x).failure()).isEqualTo(Failure.TLS_ERROR));
    }

    @Test
    void cqlDeLaDescendance() {
        client.descendants(http("PAT-CONF"), "100", 0, 100);
        wiki.verify(getRequestedFor(urlPathEqualTo(BASE + "/content/search"))
                .withQueryParam("cql", matching("ancestor=100 and type=page")).withQueryParam("expand", equalTo("version")));
    }
}
