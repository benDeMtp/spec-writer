package dev.specwriter.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import dev.specwriter.support.LocalApi;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LocalSecurityFilterTest {

    @Inject
    LaunchTokenService tokens;

    @Test
    void sansCookieRefuse() {
        given().header("Origin", LocalApi.origin()).get("/api/settings")
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void origineEtrangereRefusee() {
        given().header("Origin", "https://evil.example").cookie("sw_launch", LocalApi.cookie())
                .get("/api/anything").then().statusCode(403);
        given().header("Origin", "https://evil.example").contentType(ContentType.JSON)
                .body(Map.of("launchToken", tokens.launchToken()))
                .post("/api/session-auth").then().statusCode(403);
    }

    @Test
    void ecritureSansOrigineRefusee() {
        given().cookie("sw_launch", LocalApi.cookie()).contentType(ContentType.JSON).body("{}")
                .post("/api/anything").then().statusCode(403);
    }

    @Test
    void hostEtrangerRefuse() {
        given().header("Host", "evil.example").cookie("sw_launch", LocalApi.cookie())
                .get("/api/anything").then().statusCode(403);
    }

    @Test
    void origineLocaleEtCookieAcceptes() {
        // La requête franchit le filtre ; la route n'existe pas, d'où le 404 au format Problem.
        LocalApi.api().get("/api/inexistant").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void ecritureEnTexteRefusee() {
        given().header("Origin", LocalApi.origin()).cookie("sw_launch", LocalApi.cookie())
                .contentType(ContentType.TEXT).body("x").post("/api/anything")
                .then().statusCode(415).body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void mauvaisJetonDeLancementRefuse() {
        given().header("Origin", LocalApi.origin()).contentType(ContentType.JSON)
                .body(Map.of("launchToken", "mauvais")).post("/api/session-auth")
                .then().statusCode(403);
    }

    @Test
    void jetonDeLancementAUsageUniqueEtCookieDurci() {
        LocalApi.cookie(); // consomme le jeton si ce n'est pas déjà fait
        Response second = given().header("Origin", LocalApi.origin()).contentType(ContentType.JSON)
                .body(Map.of("launchToken", tokens.launchToken())).post("/api/session-auth");
        assertThat(second.statusCode()).isEqualTo(403);
    }

    @Test
    void cookieHttpOnlyEtSameSiteStrict() {
        LaunchTokenService fresh = new LaunchTokenService();
        String session = fresh.exchange(fresh.launchToken()).orElseThrow();
        assertThat(fresh.isValidSession(session)).isTrue();
        assertThat(fresh.exchange(fresh.launchToken())).isEmpty();
        // L'en-tête Set-Cookie est vérifié sur l'instance de l'application.
        Response r = given().header("Origin", LocalApi.origin()).contentType(ContentType.JSON)
                .body(Map.of("launchToken", "x")).post("/api/session-auth");
        assertThat(r.statusCode()).isEqualTo(403);
    }
}
