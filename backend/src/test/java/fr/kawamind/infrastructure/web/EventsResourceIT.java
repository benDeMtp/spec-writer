package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static fr.kawamind.support.LocalApi.cookie;
import static fr.kawamind.support.LocalApi.origin;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import fr.kawamind.domain.port.AppEvents;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceUpdated;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** T043, T044 : flux des sources mises à jour et réglage des notifications (017, contracts/events.md). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class EventsResourceIT {

    @Inject
    AppEvents events;

    static SourceUpdated update(String label) {
        return new SourceUpdated(UUID.randomUUID(), UUID.randomUUID(), "Projet", "JIRA", "JIRA", label,
                new SourceChange(1, 2, 0), Instant.parse("2026-11-19T10:00:00Z"));
    }

    /** Ouvre le flux et rend les {@code count} premiers événements, lignes {@code id/event/data}. */
    CompletableFuture<List<String>> listen(String lastEventId, int count) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin() + "/api/events"))
                .header("Origin", origin()).header("Cookie", "sw_launch=" + cookie())
                .header("Accept", "text/event-stream");
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<InputStream> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new AssertionError(response.statusCode() + " " + new String(response.body().readAllBytes(),
                    StandardCharsets.UTF_8));
        }
        return CompletableFuture.supplyAsync(() -> {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                int seen = 0;
                while (seen < count && (line = reader.readLine()) != null) {
                    if (!line.isBlank() && !line.startsWith(":")) {
                        lines.add(line);
                        if (line.startsWith("data:")) {
                            seen++;
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return lines;
        });
    }

    @Test
    void uneMiseAJourArriveSurLeFluxPuisEstRenvoyeeALaReconnexion() throws Exception {
        CompletableFuture<List<String>> first = listen(null, 2);
        Thread.sleep(300);
        SourceUpdated a = update("Jira FACT");
        SourceUpdated b = update("Jira AUR");
        events.publish(a);
        events.publish(b);
        List<String> lines = first.get(10, TimeUnit.SECONDS);
        assertThat(lines).contains("id:" + a.id(), "event:source-updated", "id:" + b.id());
        assertThat(lines.stream().filter(l -> l.startsWith("data:")).findFirst().orElseThrow())
                .contains("\"sourceLabel\":\"Jira FACT\"").contains("\"added\":1").contains("\"modified\":2")
                .contains("\"sourceKind\":\"JIRA\"");

        // reconnexion après a : b est renvoyé
        List<String> missed = listen(a.id().toString(), 1).get(10, TimeUnit.SECONDS);
        assertThat(missed).contains("id:" + b.id()).doesNotContain("id:" + a.id());
    }

    @Test
    void sansSessionLeFluxEstRefuse() throws Exception {
        HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                HttpRequest.newBuilder(URI.create(origin() + "/api/events")).header("Origin", origin()).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void reglageDesNotifications() {
        api().get("/api/settings/notifications").then().statusCode(200).body("sourceUpdates", equalTo(false));
        api().body(Map.of("sourceUpdates", true)).put("/api/settings/notifications").then().statusCode(200)
                .body("sourceUpdates", equalTo(true));
        api().get("/api/settings/notifications").then().body("sourceUpdates", equalTo(true));
        given().contentType("application/json").body(Map.of("sourceUpdates", false)).put("/api/settings/notifications")
                .then().statusCode(403);
        api().body(Map.of("sourceUpdates", false)).put("/api/settings/notifications").then().statusCode(200);
    }
}
