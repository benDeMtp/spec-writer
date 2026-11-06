package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 004, US2 : fichiers d'un dossier de documentation servis par l'outil, confinés (research.md R3). */
@QuarkusTest
class DocFileResourceIT {

    Path root;
    Path folder;
    String projectId;
    String otherId;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory("doc-files");
        folder = Files.createDirectories(root.resolve("site"));
        Files.createDirectories(folder.resolve("adr"));
        Files.writeString(folder.resolve("adr/0001-x.html"), "<html><body>ADR 1<script>alert(1)</script></body></html>");
        Files.createDirectories(folder.resolve("css"));
        Files.writeString(folder.resolve("css/site.css"), "body{}");
        Files.write(folder.resolve("adr/schema.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Files.writeString(folder.resolve("js.js"), "alert(1)");
        Files.writeString(folder.resolve("notes.txt"), "texte");
        Files.writeString(root.resolve("secret.html"), "<html>secret</html>");
        Files.createSymbolicLink(folder.resolve("lien.html"), root.resolve("secret.html"));
        projectId = project(folder);
        Path otherFolder = Files.createDirectories(root.resolve("autre"));
        Files.writeString(otherFolder.resolve("autre.html"), "<html>autre</html>");
        otherId = project(otherFolder);
    }

    String project(Path docs) {
        String id = api().body(Map.of("name", "Fichiers " + UUID.randomUUID())).post("/api/projects").then()
                .statusCode(201).extract().path("id");
        api().body(Map.of("kind", "FOLDER", "location", docs.toString())).post("/api/projects/" + id + "/doc-sources")
                .then().statusCode(201);
        return id;
    }

    @AfterEach
    void cleanUp() {
        for (String id : new String[] {projectId, otherId}) {
            api().delete("/api/projects/" + id + "/indexations/current");
            api().delete("/api/projects/" + id + "?sessions=DELETE");
        }
    }

    Response get(String path) {
        return api().get("/api/projects/" + projectId + "/doc-files/" + path);
    }

    @Test
    void pageServieSansScriptAvecSesRessources() {
        Response page = get("adr/0001-x.html");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.contentType()).startsWith("text/html");
        assertThat(page.header("Content-Security-Policy")).isEqualTo(
                "sandbox allow-same-origin; script-src 'none'; object-src 'none'; default-src 'self' 'unsafe-inline'");
        assertThat(page.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(page.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(page.header("Cache-Control")).isEqualTo("no-store");
        assertThat(get("css/site.css").contentType()).startsWith("text/css");
        assertThat(get("adr/schema.png").contentType()).isEqualTo("image/png");
    }

    @Test
    void cheminsHorsDuDossierRefuses() {
        for (String path : new String[] {"../secret.html", "%2e%2e/secret.html", "adr/../../secret.html",
                "lien.html", root.resolve("secret.html").toString().substring(1)}) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(404);
        }
        assertThat(api().get("/api/projects/" + projectId + "/doc-files//" + root.resolve("secret.html")).statusCode())
                .isEqualTo(404);
    }

    @Test
    void extensionsDossiersEtAutresProjetsRefuses() {
        for (String path : new String[] {"js.js", "notes.txt", "absent.html", "adr", "adr/"}) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(404);
        }
        assertThat(get("autre.html").statusCode()).isEqualTo(404);
        assertThat(api().get("/api/projects/" + otherId + "/doc-files/autre.html").statusCode()).isEqualTo(200);
        assertThat(api().get("/api/projects/" + UUID.randomUUID() + "/doc-files/adr/0001-x.html").statusCode())
                .isEqualTo(404);
    }

    @Test
    void sansSessionRefuse() {
        int status = given().get("/api/projects/" + projectId + "/doc-files/adr/0001-x.html").statusCode();
        assertThat(status).isIn(401, 403);
    }
}
