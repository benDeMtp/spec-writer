package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpMethod;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 009 : 256 Ko hors des routes d'envoi d'un document joint (research.md R1). */
@QuarkusTest
class BodySizeFilterIT {

    @Test
    void corpsTropGrandRefuseHorsEnvoi() {
        api().body(Map.of("need", "x".repeat(300_000), "name", "n")).post("/api/sessions").then().statusCode(413)
                .body("code", equalTo("PAYLOAD_TOO_LARGE"));
        api().body(Map.of("need", "trop court")).post("/api/sessions").then().statusCode(422);
    }

    @Test
    void routesDEnvoi() {
        assertThat(BodySizeFilter.isUpload(HttpMethod.POST, "/api/attachment-drafts")).isTrue();
        assertThat(BodySizeFilter.isUpload(HttpMethod.POST, "/api/sessions/abc/attachments")).isTrue();
        assertThat(BodySizeFilter.isUpload(HttpMethod.POST, "/api/sessions/abc/answers")).isFalse();
        assertThat(BodySizeFilter.isUpload(HttpMethod.PUT, "/api/attachment-drafts")).isFalse();
        assertThat(BodySizeFilter.allowed("262144", null)).isTrue();
        assertThat(BodySizeFilter.allowed("262145", null)).isFalse();
        assertThat(BodySizeFilter.allowed(null, "chunked")).isFalse();
        assertThat(BodySizeFilter.allowed(null, null)).isTrue();
        assertThat(BodySizeFilter.allowed("abc", null)).isFalse();
    }
}
