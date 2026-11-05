package dev.specwriter.infrastructure.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.JiraSearchPage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T043 : pages de tickets du projet et recherche par mot-clé, lues par 25 (FR-020 à FR-021a de 002). */
@QuarkusTest
class JiraIssuePagingIT {

    static WireMockServer jira;

    @Inject
    JiraRestClient client;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(okJson("""
                {"startAt":25,"maxResults":25,"total":120,"issues":[
                {"key":"FACT-95","fields":{"summary":"Relance","status":{"name":"À faire"},
                "issuetype":{"name":"Story"},"updated":"2026-10-30T10:15:00.000+0100"}}]}""")));
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    static JiraEndpoint endpoint() {
        return new JiraEndpoint("http://127.0.0.1:" + jira.port(), "TEST-JIRA-PAT", null);
    }

    @Test
    void ticketsDuProjetDuPlusRecentAuPlusAncien() {
        JiraSearchPage page = client.projectIssues(endpoint(), "FACT", 25, 25);
        assertThat(page.total()).isEqualTo(120);
        assertThat(page.startAt()).isEqualTo(25);
        assertThat(page.issues()).extracting(JiraIssueSummary::key).containsExactly("FACT-95");
        assertThat(page.issues().getFirst().updated()).isEqualTo(Instant.parse("2026-10-30T09:15:00Z"));
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/search"))
                .withQueryParam("jql", equalTo("project = FACT ORDER BY updated DESC"))
                .withQueryParam("startAt", equalTo("25")).withQueryParam("maxResults", equalTo("25")));
    }

    @Test
    void motCleEchappeDansLaJql() {
        client.searchText(endpoint(), "FACT", "relance \"urgente\" OR project != X", 50, 25);
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/search"))
                .withQueryParam("jql", equalTo("project = FACT AND text ~ \"relance \\\"urgente\\\" OR project = X\" ORDER BY updated DESC"))
                .withQueryParam("startAt", equalTo("50")).withQueryParam("maxResults", equalTo("25")));
    }
}
