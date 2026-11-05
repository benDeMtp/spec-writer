package dev.specwriter.infrastructure.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.port.IssueTracker;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JiraRestClientIT {

    static WireMockServer jira;
    static TestCertificates certs;

    @Inject
    JiraRestClient client;

    @BeforeAll
    static void start() throws Exception {
        certs = TestCertificates.generate(Path.of("target/test-certs"));
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().dynamicHttpsPort()
                .keystorePath(certs.serverKeystore.toString()).keystorePassword(TestCertificates.PASSWORD)
                .keyManagerPassword(TestCertificates.PASSWORD).keystoreType("PKCS12"));
        jira.start();
        jira.stubFor(get("/rest/api/2/serverInfo").willReturn(okJson("{\"version\":\"9.12.3\",\"deploymentType\":\"Server\"}")));
        jira.stubFor(get("/rest/api/2/myself").withHeader("Authorization", equalTo("Bearer TEST-JIRA-PAT"))
                .willReturn(okJson("{\"name\":\"ada\"}")));
        jira.stubFor(get("/rest/api/2/myself").withHeader("Authorization", equalTo("Bearer mauvais"))
                .willReturn(aResponse().withStatus(401)));
        jira.stubFor(get("/rest/api/2/project/PROJ").willReturn(okJson("{\"key\":\"PROJ\"}")));
        jira.stubFor(get("/rest/api/2/project/NOPE").willReturn(aResponse().withStatus(404)));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/mypermissions")).willReturn(okJson("""
                {"permissions":{"BROWSE_PROJECTS":{"havePermission":true},"CREATE_ISSUES":{"havePermission":true},
                "EDIT_ISSUES":{"havePermission":false}}}""")));
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    private static JiraEndpoint http(String token) {
        return new JiraEndpoint("http://127.0.0.1:" + jira.port(), token, null);
    }

    @Test
    void appelsDuTestDeConnexionAvecJetonBearer() {
        JiraEndpoint e = http("TEST-JIRA-PAT");
        assertThat(client.serverVersion(e)).isEqualTo("9.12.3");
        assertThat(client.currentUser(e)).isEqualTo("ada");
        client.project(e, "PROJ");
        Map<String, Boolean> perms = client.permissions(e, "PROJ",
                Set.of(IssueTracker.PERM_BROWSE, IssueTracker.PERM_CREATE, IssueTracker.PERM_EDIT));
        assertThat(perms).containsEntry("BROWSE_PROJECTS", true).containsEntry("CREATE_ISSUES", true)
                .containsEntry("EDIT_ISSUES", false);
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/mypermissions"))
                .withQueryParam("projectKey", equalTo("PROJ"))
                .withQueryParam("permissions", equalTo("BROWSE_PROJECTS,CREATE_ISSUES,EDIT_ISSUES"))
                .withHeader("Authorization", equalTo("Bearer TEST-JIRA-PAT")));
    }

    @Test
    void erreursClassees() {
        assertThatThrownBy(() -> client.currentUser(http("mauvais")))
                .isInstanceOf(IssueTrackerException.class).extracting(e -> ((IssueTrackerException) e).kind())
                .isEqualTo(IssueTrackerException.Kind.UNAUTHORIZED);
        assertThatThrownBy(() -> client.project(http("TEST-JIRA-PAT"), "NOPE"))
                .extracting(e -> ((IssueTrackerException) e).kind()).isEqualTo(IssueTrackerException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> client.serverVersion(new JiraEndpoint("http://127.0.0.1:1", "x", null)))
                .extracting(e -> ((IssueTrackerException) e).kind()).isEqualTo(IssueTrackerException.Kind.UNREACHABLE);
    }

    @Test
    void httpsAvecAutoriteInterneAcceptee() {
        JiraEndpoint e = new JiraEndpoint("https://127.0.0.1:" + jira.httpsPort(), "TEST-JIRA-PAT", certs.caPem.toString());
        assertThat(client.serverVersion(e)).isEqualTo("9.12.3");
    }

    @Test
    void httpsSansLaBonneAutoriteRefuse() {
        JiraEndpoint sansCa = new JiraEndpoint("https://127.0.0.1:" + jira.httpsPort(), "TEST-JIRA-PAT", null);
        assertThatThrownBy(() -> client.serverVersion(sansCa))
                .extracting(e -> ((IssueTrackerException) e).kind()).isEqualTo(IssueTrackerException.Kind.UNREACHABLE);
        JiraEndpoint autreCa = new JiraEndpoint("https://127.0.0.1:" + jira.httpsPort(), "TEST-JIRA-PAT", certs.otherCaPem.toString());
        assertThatThrownBy(() -> client.serverVersion(autreCa))
                .extracting(e -> ((IssueTrackerException) e).kind()).isEqualTo(IssueTrackerException.Kind.UNREACHABLE);
    }

    /** T063 : 20 recherches simultanées et une nouvelle tentative après 429, à 8 requêtes par seconde au plus (FR-022a). */
    @Test
    void auPlusHuitRequetesParSecondeMemeAvecUneNouvelleTentative() throws Exception {
        String empty = "{\"startAt\":0,\"maxResults\":25,\"total\":0,\"issues\":[]}";
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching("project = LIM .*"))
                .inScenario("débit").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")).willSetStateTo("servi"));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching("project = LIM .*"))
                .inScenario("débit").whenScenarioStateIs("servi").willReturn(okJson(empty)));
        jira.resetRequests();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> all = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int n = i;
                all.add(pool.submit(() -> client.searchText(http("TEST-JIRA-PAT"), "LIM", "terme" + n, 0, 25)));
            }
            for (Future<?> f : all) {
                f.get();
            }
        }
        List<Long> at = jira.getAllServeEvents().stream().filter(e -> e.getRequest().getUrl().contains("LIM"))
                .map(ServeEvent::getRequest).map(r -> r.getLoggedDate().getTime()).sorted().toList();
        // 20 réponses servies, plus au moins un 429 suivi de sa nouvelle tentative
        assertThat(at).hasSizeGreaterThan(20);
        for (long start : at) {
            assertThat(at.stream().filter(t -> t >= start && t < start + 1000).count()).as("requêtes reçues en une seconde")
                    .isLessThanOrEqualTo(8);
        }
    }
}
