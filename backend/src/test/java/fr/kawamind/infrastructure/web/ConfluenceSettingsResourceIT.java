package fr.kawamind.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import fr.kawamind.domain.port.SecretStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T024 (016) : {@code /api/settings/confluence} ; le jeton n'est jamais renvoyé. */
@QuarkusTest
class ConfluenceSettingsResourceIT {

    static WireMockServer wiki;

    @Inject
    SecretStore secrets;

    @BeforeAll
    static void start() {
        wiki = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        wiki.start();
    }

    @AfterAll
    static void stop() {
        wiki.stop();
    }

    @BeforeEach
    void reset() {
        wiki.resetAll();
        api().delete("/api/settings/confluence").then().statusCode(204);
    }

    static Map<String, Object> body(String token) {
        Map<String, Object> body = new HashMap<>(Map.of("serverUrl", "http://127.0.0.1:" + wiki.port() + "/wiki",
                "acceptInsecureHttp", true));
        if (token != null) {
            body.put("token", token);
        }
        return body;
    }

    @Test
    void sansServeurRienNEstDeclare() {
        api().get("/api/settings/confluence").then().statusCode(200).body("enabled", org.hamcrest.Matchers.is(false));
        api().post("/api/settings/confluence/test").then().statusCode(409).body("code",
                org.hamcrest.Matchers.is("CONFLUENCE_NOT_CONFIGURED"));
    }

    @Test
    void enregistrementJetonGardeEtJamaisRenvoye() {
        api().body(body("PAT-SECRET-123")).put("/api/settings/confluence").then().statusCode(200)
                .body("serverUrl", org.hamcrest.Matchers.endsWith("/wiki")).body("tokenSet", org.hamcrest.Matchers.is(true))
                .body(not(containsString("PAT-SECRET-123")));
        // sans jeton : celui du trousseau est gardé
        api().body(body(null)).put("/api/settings/confluence").then().statusCode(200)
                .body("tokenSet", org.hamcrest.Matchers.is(true));
        api().get("/api/settings/confluence").then().statusCode(200).body(not(containsString("PAT-SECRET-123")))
                .body("insecureHttp", org.hamcrest.Matchers.is(true));
    }

    @Test
    void httpSansConfirmationRefuse() {
        Map<String, Object> b = body("x");
        b.put("acceptInsecureHttp", false);
        api().body(b).put("/api/settings/confluence").then().statusCode(422)
                .body("userMessage", containsString("http"));
    }

    @Test
    void testDeConnexion() {
        api().body(body("PAT-CONF")).put("/api/settings/confluence").then().statusCode(200);
        wiki.stubFor(get("/wiki/rest/api/user/current").withHeader("Authorization", equalTo("Bearer PAT-CONF"))
                .willReturn(okJson("{\"username\":\"ada\",\"displayName\":\"Ada\"}")));
        wiki.stubFor(get("/wiki/rest/api/settings/systemInfo").willReturn(okJson("{\"version\":\"8.5.4\"}")));
        api().post("/api/settings/confluence/test").then().statusCode(200).body("result", org.hamcrest.Matchers.is("OK"))
                .body("account", org.hamcrest.Matchers.is("Ada")).body("serverVersion", org.hamcrest.Matchers.is("8.5.4"));
        api().get("/api/settings/confluence").then().body("lastTest.result", org.hamcrest.Matchers.is("OK"));

        wiki.resetAll();
        wiki.stubFor(get("/wiki/rest/api/user/current").willReturn(aResponse().withStatus(401)));
        api().post("/api/settings/confluence/test").then().statusCode(200)
                .body("result", org.hamcrest.Matchers.is("UNAUTHORIZED")).body("message", containsString("jeton"));

        wiki.resetAll();
        wiki.stubFor(get("/wiki/rest/api/user/current").willReturn(okJson("{\"autre\":1}")));
        api().post("/api/settings/confluence/test").then().body("result", org.hamcrest.Matchers.is("NOT_CONFLUENCE"));

        // port fermé : serveur injoignable
        api().body(Map.of("serverUrl", "http://127.0.0.1:1/wiki", "acceptInsecureHttp", true))
                .put("/api/settings/confluence").then().statusCode(200);
        api().post("/api/settings/confluence/test").then().body("result", org.hamcrest.Matchers.is("UNREACHABLE"));
    }

    @Test
    void retraitOteLeJetonDuTrousseau() {
        api().body(body("PAT-CONF")).put("/api/settings/confluence").then().statusCode(200);
        api().delete("/api/settings/confluence").then().statusCode(204);
        org.assertj.core.api.Assertions.assertThat(secrets.get(SecretStore.CONFLUENCE_PAT)).isEmpty();
        api().get("/api/settings/confluence").then().body("serverUrl", nullValue());
    }
}
