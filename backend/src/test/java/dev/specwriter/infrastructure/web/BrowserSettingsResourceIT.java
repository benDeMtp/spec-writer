package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@code /api/settings/browser} (013, T057), avec les navigateurs simulés du profil test. */
@QuarkusTest
class BrowserSettingsResourceIT {

    @AfterEach
    void systemDefault() {
        api().body(Map.of()).put("/api/settings/browser").then().statusCode(200);
    }

    @Test
    void choisirUnNavigateurInstalleEtYOuvrirSpecWriter() {
        api().get("/api/settings/browser").then().statusCode(200).body("selected", nullValue())
                .body("missing", equalTo(false))
                .body("installed.name", contains("Navigateur simulé A", "Navigateur simulé B"));

        api().body(Map.of("browserId", "simule-b")).put("/api/settings/browser").then().statusCode(200)
                .body("selected", equalTo("simule-b"));
        api().get("/api/settings/browser").then().body("selected", equalTo("simule-b"));

        api().post("/api/settings/browser/open").then().statusCode(200).body("opened", equalTo(true))
                .body("browser.name", equalTo("Navigateur simulé B"));
    }

    @Test
    void unNavigateurAbsentEstRefuse() {
        api().body(Map.of("browserId", "netscape")).put("/api/settings/browser").then().statusCode(422)
                .body("userMessage", containsString("pas installé"));
        api().get("/api/settings/browser").then().body("selected", nullValue());
    }
}
