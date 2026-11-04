package dev.specwriter.infrastructure.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.specwriter.domain.jira.IssueLinkType;
import dev.specwriter.domain.jira.IssueStatus;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.jira.TrackedIssuePage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T101 : lectures et écritures Jira de la connaissance du projet (research.md R20, R23). */
@QuarkusTest
class JiraKnowledgeSyncIT {

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
    void ticketsModifiesDepuisLeRepereDuPlusAncienAuPlusRecent() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(okJson("""
                {"startAt":0,"maxResults":50,"total":1,"issues":[
                 {"key":"FACT-7","fields":{"summary":"Annuler une facture",
                  "description":"h2. Besoin\\nAnnuler par un *avoir total*.",
                  "status":{"name":"Terminé","statusCategory":{"key":"done"}},
                  "issuetype":{"name":"Story"},"updated":"2026-09-12T10:00:00.000+0000"}}]}""")));
        TrackedIssuePage page = client.searchUpdatedSince(endpoint(), "FACT", Instant.parse("2026-10-20T08:30:00Z"),
                0, 50);
        assertThat(page.last()).isTrue();
        TrackedIssue issue = page.issues().get(0);
        assertThat(issue.key()).isEqualTo("FACT-7");
        assertThat(issue.description()).contains("## Besoin").contains("**avoir total**");
        assertThat(issue.status()).isEqualTo("Terminé");
        assertThat(issue.statusCategory()).isEqualTo("done");
        assertThat(issue.issueType()).isEqualTo("Story");
        assertThat(issue.updated()).isEqualTo(Instant.parse("2026-09-12T10:00:00Z"));
        // marge d'un jour : la date JQL est lue dans le fuseau de l'utilisateur Jira
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/search"))
                .withQueryParam("jql", equalTo("project = FACT AND updated >= \"2026/10/19 08:30\" ORDER BY updated ASC"))
                .withQueryParam("startAt", equalTo("0")).withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("fields", equalTo("summary,description,status,issuetype,updated")));
    }

    @Test
    void premiereSynchronisationSansRepere() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search"))
                .willReturn(okJson("{\"startAt\":0,\"maxResults\":50,\"total\":0,\"issues\":[]}")));
        assertThat(client.searchUpdatedSince(endpoint(), "FACT", null, 0, 50).issues()).isEmpty();
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/search"))
                .withQueryParam("jql", equalTo("project = FACT ORDER BY updated ASC")));
    }

    @Test
    void listeDesClesSeulementParPages() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson("""
                        {"startAt":0,"maxResults":2,"total":3,"issues":[{"key":"FACT-7"},{"key":"FACT-9"}]}""")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("startAt", equalTo("2"))
                .willReturn(okJson("""
                        {"startAt":2,"maxResults":2,"total":3,"issues":[{"key":"FACT-11"}]}""")));
        assertThat(client.listKeys(endpoint(), "FACT")).containsExactly("FACT-7", "FACT-9", "FACT-11");
        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/2/search")).withQueryParam("fields", equalTo("key"))
                .withQueryParam("jql", equalTo("project = FACT ORDER BY key ASC")));
    }

    @Test
    void statutRelueEtTicketSupprime() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-9")).withQueryParam("fields", equalTo("status,updated"))
                .willReturn(okJson("""
                        {"key":"FACT-9","fields":{"status":{"name":"En cours","statusCategory":{"key":"indeterminate"}},
                         "updated":"2026-11-03T10:00:00.000+0000"}}""")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-8")).willReturn(aResponse().withStatus(404)));
        IssueStatus status = client.issueStatus(endpoint(), "FACT-9");
        assertThat(status).isEqualTo(new IssueStatus("FACT-9", "En cours", "indeterminate",
                Instant.parse("2026-11-03T10:00:00Z")));
        assertThat(status.closed()).isFalse();
        assertThatThrownBy(() -> client.issueStatus(endpoint(), "FACT-8"))
                .isInstanceOfSatisfying(IssueTrackerException.class,
                        e -> assertThat(e.kind()).isEqualTo(IssueTrackerException.Kind.NOT_FOUND));
    }

    @Test
    void typesDeLiensEtCreationDuLien() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issueLinkType")).willReturn(okJson("""
                {"issueLinkTypes":[{"id":"10000","name":"Blocks","inward":"is blocked by","outward":"blocks"},
                 {"id":"10003","name":"Relates","inward":"relates to","outward":"relates to"}]}""")));
        jira.stubFor(post(urlPathEqualTo("/rest/api/2/issueLink")).willReturn(aResponse().withStatus(201)));
        List<IssueLinkType> types = client.linkTypes(endpoint());
        assertThat(types).extracting(IssueLinkType::name).containsExactly("Blocks", "Relates");
        assertThat(types).filteredOn(IssueLinkType::relates).extracting(IssueLinkType::name).containsExactly("Relates");

        client.createLink(endpoint(), "Relates", "FACT-20", "FACT-7");
        jira.verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issueLink")).withRequestBody(equalToJson("""
                {"type":{"name":"Relates"},"outwardIssue":{"key":"FACT-20"},"inwardIssue":{"key":"FACT-7"}}""")));
    }

    @Test
    void auPlusHuitRequetesParSeconde() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-9")).willReturn(okJson("""
                {"key":"FACT-9","fields":{"status":{"name":"À faire","statusCategory":{"key":"new"}}}}""")));
        // chauffe : la première connexion arrive en retard et fausserait la mesure à l'arrivée
        client.issueStatus(endpoint(), "FACT-9");
        try {
            Thread.sleep(1_200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        jira.resetRequests();
        for (int i = 0; i < 20; i++) {
            client.issueStatus(endpoint(), "FACT-9");
        }
        List<Long> times = jira.findAll(getRequestedFor(urlPathEqualTo("/rest/api/2/issue/FACT-9"))).stream()
                .map(LoggedRequest::getLoggedDate).map(java.util.Date::getTime).sorted().toList();
        assertThat(times).hasSize(20);
        for (int i = 8; i < times.size(); i++) {
            // la 9e requête d'une fenêtre arrive au moins une seconde après la 1re
            assertThat(times.get(i) - times.get(i - 8)).isGreaterThanOrEqualTo(990);
        }
    }
}
