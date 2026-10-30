package dev.specwriter.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Profil dédié : application neuve, jeton de lancement pas encore consommé. */
@QuarkusTest
@TestProfile(SessionAuthCookieTest.Fresh.class)
class SessionAuthCookieTest {

    public static class Fresh implements QuarkusTestProfile {
    }

    @Inject
    LaunchTokenService tokens;

    @Test
    void echangeDonneUnCookieHttpOnlySameSiteStrict() {
        String setCookie = given().header("Origin", "http://127.0.0.1:" + RestAssured.port)
                .contentType(ContentType.JSON)
                .body(Map.of("launchToken", tokens.launchToken()))
                .post("/api/session-auth")
                .then().statusCode(204)
                .extract().header("Set-Cookie");
        assertThat(setCookie).startsWith("sw_launch=").contains("HttpOnly").containsIgnoringCase("SameSite=Strict")
                .contains("Path=/");
    }
}
