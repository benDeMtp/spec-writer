package fr.kawamind.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import fr.kawamind.support.LocalApi;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SpaFallbackTest {

    @Test
    void routeDuFrontRenvoieIndexHtml() {
        given().get("/questionnaire/123").then().statusCode(200).body(containsString("<sw-root>"));
    }

    /**
     * Après une mise à jour, la page doit charger le nouveau front : {@code index.html} n'est jamais
     * gardé en cache (il nomme les fichiers du front, qui changent de nom à chaque build), alors que
     * ces fichiers à empreinte le sont.
     */
    @Test
    void indexHtmlNEstPasGardeEnCache() {
        given().get("/").then().statusCode(200).header("Cache-Control", equalTo("no-cache"));
        given().get("/index.html").then().statusCode(200).header("Cache-Control", equalTo("no-cache"));
        given().get("/seances/123").then().statusCode(200).header("Cache-Control", equalTo("no-cache"));
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
