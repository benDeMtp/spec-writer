package dev.specwriter.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import dev.specwriter.support.LocalApi;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SpaFallbackTest {

    @Test
    void routeDuFrontRenvoieIndexHtml() {
        given().get("/questionnaire/123").then().statusCode(200).body(containsString("<app-root>"));
    }

    @Test
    void apiInconnueRenvoieUnProblem404() {
        LocalApi.api().get("/api/inconnu").then().statusCode(404)
                .contentType("application/problem+json").body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void reglesDeRoutage() {
        assertThat(SpaFallbackRoute.isFrontRoute("/sessions")).isTrue();
        assertThat(SpaFallbackRoute.isFrontRoute("/api/sessions")).isFalse();
        assertThat(SpaFallbackRoute.isFrontRoute("/q/health")).isFalse();
        assertThat(SpaFallbackRoute.isFrontRoute("/main-ABC.js")).isFalse();
        assertThat(SpaFallbackRoute.isFrontRoute("/")).isFalse();
    }
}
