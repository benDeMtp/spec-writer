package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T120 : export Markdown d'une version (FR-019, FR-020). LLM rejoué. */
@QuarkusTest
class ExportResourceIT {

    @BeforeEach
    void consent() {
        SessionResourceIT.copilotConsent(true);
    }

    static String generated() {
        JsonPath created = SessionResourceIT.create("IT-OK");
        String id = created.getString("id");
        List<String> q = created.getList("currentBatch.id");
        api().body(Map.of("value", "Gagner du temps")).put("/api/sessions/" + id + "/questions/" + q.get(0) + "/answer")
                .then().statusCode(200);
        api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + q.get(1) + "/answer").then().statusCode(200);
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201);
        return id;
    }

    @Test
    void markdownDeLaVersionAvecNomDeFichierNettoye() {
        String id = generated();
        api().body(Map.of("name", "Salles / ../du\\siège : été")).patch("/api/sessions/" + id).then().statusCode(200);
        String markdown = api().get("/api/sessions/" + id + "/versions/1").then().extract().path("markdown");

        Response r = api().get("/api/sessions/" + id + "/versions/1/export");
        r.then().statusCode(200).contentType(startsWith("text/markdown"));
        assertThat(r.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(new String(r.asByteArray(), StandardCharsets.UTF_8)).isEqualTo(markdown);
        assertThat(markdown).contains("## Résumé").contains("## Use cases").contains("## Hypothèses").contains("SC-01-01");

        String disposition = r.getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;");
        String ascii = disposition.replaceAll(".*filename=\"([^\"]*)\".*", "$1");
        assertThat(ascii).isEqualTo("Salles-du-siege-ete-v1.md").doesNotContain("/").doesNotContain("\\").doesNotContain("..");
        assertThat(disposition).contains("filename*=UTF-8''Salles-du-si%C3%A8ge-%C3%A9t%C3%A9-v1.md");
    }

    @Test
    void versionOuSessionAbsente() {
        String id = generated();
        api().get("/api/sessions/" + id + "/versions/9/export").then().statusCode(404);
        api().get("/api/sessions/00000000-0000-0000-0000-000000000000/versions/1/export").then().statusCode(404);
        api().get("/api/sessions/pas-un-uuid/versions/1/export").then().statusCode(404);
    }
}
