package dev.specwriter.support;

import static io.restassured.RestAssured.given;

import dev.specwriter.infrastructure.web.LaunchTokenService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.enterprise.inject.spi.CDI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Requêtes de test authentifiées comme le front : Origin locale et cookie {@code sw_launch}
 * obtenu une fois par instance d'application (le jeton de lancement est à usage unique).
 */
public final class LocalApi {

    private static final Map<Integer, String> COOKIES = new ConcurrentHashMap<>();

    private LocalApi() {
    }

    public static String origin() {
        return "http://127.0.0.1:" + RestAssured.port;
    }

    public static String cookie() {
        LaunchTokenService tokens = CDI.current().select(LaunchTokenService.class).get();
        return COOKIES.computeIfAbsent(System.identityHashCode(tokens), k -> given()
                .header("Origin", origin())
                .contentType(ContentType.JSON)
                .body(Map.of("launchToken", tokens.launchToken()))
                .post("/api/session-auth")
                .then().statusCode(204)
                .extract().cookie("sw_launch"));
    }

    /** Requête authentifiée, JSON. */
    public static RequestSpecification api() {
        return given().header("Origin", origin()).cookie("sw_launch", cookie()).contentType(ContentType.JSON);
    }
}
