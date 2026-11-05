package dev.specwriter.infrastructure.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraSearchPage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T100 : recherche, lecture et mise à jour de la description, limitation de débit. */
@QuarkusTest
class JiraIssueClientIT {

    static WireMockServer jira;

    @Inject
    JiraRestClient client;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    @BeforeEach
    void reset() {
        jira.resetAll();
    }

    static JiraEndpoint endpoint() {
        return new JiraEndpoint("http://127.0.0.1:" + jira.port(), "TEST-JIRA-PAT", null);
    }

    @Test
    void recherchePaginee() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(okJson("""
                {"startAt":20,"maxResults":2,"total":42,"issues":[
                 {"key":"PROJ-1","fields":{"summary":"Réserver","status":{"name":"À faire"},
                  "issuetype":{"name":"Story"},"updated":"2026-10-30T10:15:00.000+0100"}},
                 {"key":"PROJ-2","fields":{"summary":"Payer","updated":"2026-10-29T08:00:00.000+0000"}}]}""")));
        JiraSearchPage page = client.searchText(endpoint(), "PROJ", "x", 20, 2);
        assertThat(page.total()).isEqualTo(42);
        assertThat(page.startAt()).isEqualTo(20);
        assertThat(page.issues()).hasSize(2);
        assertThat(page.issues().get(0).key()).isEqualTo("PROJ-1");
        assertThat(page.issues().get(0).status()).isEqualTo("À faire");
        assertThat(page.issues().get(0).issueType()).isEqualTo("Story");
        assertThat(page.issues().get(0).updated()).isEqualTo(Instant.parse("2026-10-30T09:15:00Z"));
        assertThat(page.issues().get(1).status()).isNull();
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/search"))
                .withQueryParam("jql", equalTo("project = PROJ AND text ~ \"x\" ORDER BY updated DESC"))
                .withQueryParam("startAt", equalTo("20")).withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", equalTo("summary,status,issuetype,updated"))
                .withHeader("Authorization", equalTo("Bearer TEST-JIRA-PAT")));
    }

    @Test
    void lectureEtMiseAJourDeLaDescription() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/PROJ-7")).willReturn(okJson("""
                {"key":"PROJ-7","fields":{"summary":"Titre","description":"h1. Desc","updated":"2026-10-30T09:00:00.000+0000"}}""")));
        jira.stubFor(put("/rest/api/2/issue/PROJ-7").willReturn(aResponse().withStatus(204)));
        JiraIssue issue = client.getIssue(endpoint(), "PROJ-7");
        assertThat(issue).isEqualTo(new JiraIssue("PROJ-7", "Titre", "h1. Desc", Instant.parse("2026-10-30T09:00:00Z")));
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/issue/PROJ-7"))
                .withQueryParam("fields", equalTo("summary,description,updated")));

        client.updateDescription(endpoint(), "PROJ-7", "h1. Nouvelle");
        jira.verify(putRequestedFor(urlPathEqualTo("/rest/api/2/issue/PROJ-7"))
                .withRequestBody(equalToJson("{\"fields\":{\"description\":\"h1. Nouvelle\"}}")));
    }

    @Test
    void descriptionAbsente() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/PROJ-8")).willReturn(okJson("""
                {"key":"PROJ-8","fields":{"summary":"Vide","description":null,"updated":"2026-10-30T09:00:00.000+0000"}}""")));
        assertThat(client.getIssue(endpoint(), "PROJ-8").description()).isEmpty();
        assertThatThrownBy(() -> client.getIssue(endpoint(), "pas une clé")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tropDeRequetesAttendRetryAfter() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/PROJ-9")).inScenario("429").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")).willSetStateTo("ok"));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/PROJ-9")).inScenario("429").whenScenarioStateIs("ok")
                .willReturn(okJson("""
                        {"key":"PROJ-9","fields":{"summary":"S","description":"d","updated":"2026-10-30T09:00:00.000+0000"}}""")));
        long start = System.nanoTime();
        assertThat(client.getIssue(endpoint(), "PROJ-9").key()).isEqualTo("PROJ-9");
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(2000);
    }

    @Test
    void troisTentativesAuMaximum() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/PROJ-10"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));
        assertThatThrownBy(() -> client.getIssue(endpoint(), "PROJ-10")).isInstanceOfSatisfying(IssueTrackerException.class,
                e -> assertThat(e.kind()).isEqualTo(IssueTrackerException.Kind.RATE_LIMITED));
        jira.verify(3, getRequestedFor(urlPathEqualTo("/rest/api/2/issue/PROJ-10")));
    }

    @Test
    void jetonRefuse() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> client.searchText(endpoint(), "PROJ", "x", 0, 10))
                .isInstanceOfSatisfying(IssueTrackerException.class, e -> {
                    assertThat(e.kind()).isEqualTo(IssueTrackerException.Kind.UNAUTHORIZED);
                    assertThat(e.errorCode()).isEqualTo(dev.specwriter.domain.error.ErrorCode.JIRA_TOKEN_REJECTED);
                });
    }
}
