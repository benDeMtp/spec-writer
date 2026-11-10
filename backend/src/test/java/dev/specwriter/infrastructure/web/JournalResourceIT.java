package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

/** T069 : {@code GET /api/journal}, alimenté par le journal des erreurs branché au démarrage (FR-031). */
@QuarkusTest
class JournalResourceIT {

    static final Logger LOG = Logger.getLogger(JournalResourceIT.class);

    @Test
    void avertissementsEtErreursExpurgesLesPlusRecentsDAbord() {
        String marker = UUID.randomUUID().toString();
        LOG.infof("info %s", marker);
        LOG.warnf("avertissement %s jeton ghp_abcdefghijklmnop", marker);
        LOG.error("erreur " + marker, new IllegalStateException("Bearer abcdefgh123456"));

        JsonPath all = api().get("/api/journal").then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> mine = all.<Map<String, Object>>getList("").stream()
                .filter(e -> String.valueOf(e.get("message")).contains(marker)).toList();
        assertThat(mine).extracting(e -> e.get("level")).containsExactly("ERROR", "WARN");
        assertThat(mine.get(1).get("message").toString()).doesNotContain("ghp_abcdefghijklmnop").contains("[MASQUÉ]");
        assertThat(mine.get(0).get("cause").toString()).doesNotContain("abcdefgh123456");
        assertThat(mine.get(0).get("logger")).isEqualTo(JournalResourceIT.class.getName());

        List<String> errors = api().queryParam("level", "ERROR").get("/api/journal").then().statusCode(200).extract()
                .jsonPath().getList("level");
        assertThat(errors).containsOnly("ERROR");
        api().queryParam("level", "INFO").get("/api/journal").then().statusCode(422);
    }

    @Test
    void purgeVideLeJournalPuisLeJournalReprend() {
        String before = UUID.randomUUID().toString();
        LOG.warnf("avant la purge %s", before);
        assertThat(api().get("/api/journal").then().statusCode(200).extract().jsonPath().getList("message").toString())
                .contains(before);

        api().delete("/api/journal").then().statusCode(204);

        String after = UUID.randomUUID().toString();
        LOG.warnf("après la purge %s", after);
        List<String> messages = api().get("/api/journal").then().statusCode(200).extract().jsonPath().getList("message");
        assertThat(messages.toString()).doesNotContain(before).contains(after);
    }
}
