package dev.specwriter.performance;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T060, SC-006 de 002 : avec un Jira de 2 000 tickets (WireMock), une page de la liste des tickets
 * et une page de la recherche unifiée s'affichent en moins de 3 s au 95ᵉ centile.
 */
@QuarkusTest
class PagingPerformanceIT {

    static final long LIMIT_MS = 3_000;
    static final int TOTAL = 2_000;

    static WireMockServer jira;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
        String issues = IntStream.range(0, 25).mapToObj(i -> """
                {"key":"PROJ-%d","fields":{"summary":"Ticket perfpage %d","status":{"name":"À faire"},
                 "issuetype":{"name":"Story"},"updated":"2026-10-%02dT10:00:00.000+0000"}}""".formatted(i, i, 28 - i % 27))
                .collect(Collectors.joining(","));
        // toutes les pages se ressemblent : seul le temps de réponse compte ici
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search"))
                .willReturn(okJson("{\"startAt\":0,\"maxResults\":25,\"total\":" + TOTAL + ",\"issues\":[" + issues + "]}")));
    }

    @AfterAll
    static void stop() {
        api().delete("/api/settings/jira");
        jira.stop();
    }

    static long p95(List<Long> durations) {
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    }

    @Test
    void pagesDeTicketsEtDeRechercheEnMoinsDeTroisSecondes() {
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        api().body(Map.of("granted", true)).put("/api/consents/JIRA").then().statusCode(204);
        api().body(Map.of("granted", true)).put("/api/consents/COPILOT").then().statusCode(204);
        for (int i = 0; i < 10; i++) {
            api().body(Map.of("need", "Les employés doivent pouvoir réserver une salle de réunion perfpage IT-OK " + i))
                    .post("/api/sessions").then().statusCode(201);
        }

        List<Long> issues = new ArrayList<>();
        for (int startAt = 0; startAt < 20 * 25; startAt += 25) {
            long start = System.nanoTime();
            api().queryParam("startAt", startAt).get("/api/jira/projects/PROJ/issues").then().statusCode(200);
            issues.add((System.nanoTime() - start) / 1_000_000);
        }
        assertThat(p95(issues)).as("95e centile des pages de tickets, en ms (%s)", issues).isLessThan(LIMIT_MS);

        List<Long> search = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 20; page++) {
            long start = System.nanoTime();
            var request = api().queryParam("q", "perfpage");
            if (cursor != null) {
                request = request.queryParam("cursor", cursor);
            }
            JsonPath body = request.get("/api/search").then().statusCode(200).extract().jsonPath();
            search.add((System.nanoTime() - start) / 1_000_000);
            assertThat(body.getList("items")).hasSizeLessThanOrEqualTo(25);
            cursor = body.getString("nextCursor");
            if (cursor == null) {
                break;
            }
        }
        assertThat(search).hasSize(20);
        assertThat(p95(search)).as("95e centile des pages de recherche, en ms (%s)", search).isLessThan(LIMIT_MS);
    }
}
