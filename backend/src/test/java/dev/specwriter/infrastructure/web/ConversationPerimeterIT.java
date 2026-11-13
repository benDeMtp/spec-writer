package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.project.Project;
import dev.specwriter.infrastructure.sources.DemoTree;
import io.restassured.path.json.JsonPath;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 013, T027 : analyse de périmètre publiée en flux, puis relue avec la conversation ; IA rejouée. */
@QuarkusTest
class ConversationPerimeterIT {

    static final String PROJECT = "/api/projects/" + Project.DEFAULT_ID;

    @TempDir
    Path dir;

    String rootId;

    @BeforeEach
    void setUp() {
        ConversationResourceIT.consent(true);
        rootId = api().body(Map.of("location", DemoTree.copy(dir).toString())).post(PROJECT + "/source-roots").then()
                .statusCode(201).extract().path("id");
    }

    @AfterEach
    void tearDown() {
        api().delete(PROJECT + "/source-roots/" + rootId).then().statusCode(204);
        ConversationResourceIT.consent(false);
    }

    @Test
    void analysePublieeDeuxFoisPuisRelueAvecLaConversation() {
        String id = api().post(PROJECT + "/conversations").then().statusCode(201).extract().path("id");
        String turn = api().body(Map.of("text", "IT-PERIMETRE Quels modules touche le statut SUSPENDU ?"))
                .post("/api/conversations/" + id + "/messages").then().statusCode(202).extract().path("turnId");

        String stream = api().accept("text/event-stream").get("/api/conversations/" + id + "/turns/" + turn + "/events")
                .then().statusCode(200).extract().asString();
        assertThat(stream.split("event:perimeter", -1)).hasSize(3);
        assertThat(stream).contains("\"qualified\":false").contains("\"qualified\":true").contains("event:done");

        JsonPath detail = api().get("/api/conversations/" + id).then().statusCode(200).extract().jsonPath();
        String analysis = "messages[1].perimeters[0]";
        assertThat(detail.getInt(analysis + ".ordinal")).isEqualTo(1);
        assertThat(detail.getList(analysis + ".terms")).containsExactly("ContractStatus", "CONTRACT_STATUS",
                "contract.status");
        List<String> names = detail.getList(analysis + ".modules.name");
        assertThat(names).containsExactlyInAnyOrder("common-contract", "quarkus-contract", "facturation", "annuaire",
                "ancien", "demo-front", "facturation-chart", "relance", "aggregates");
        // US3 : relance dépend de facturation, l'agrégateur liste common-contract ; tous deux à vérifier
        assertThat(detail.getList(analysis + ".modules.find { it.name == 'relance' }.reasons[0].chain.name"))
                .containsExactly("facturation", "relance");
        assertThat(detail.getString(analysis + ".modules.find { it.name == 'relance' }.qualification"))
                .isEqualTo("TO_CHECK");
        assertThat(detail.getString(analysis + ".modules.find { it.name == 'aggregates' }.reasons[0].of.name"))
                .isEqualTo("common-contract");
        assertThat(detail.getString(analysis + ".modules.find { it.name == 'common-contract' }.qualification"))
                .isEqualTo("IMPACTED");
        assertThat(detail.getString(analysis + ".modules.find { it.name == 'annuaire' }.qualification"))
                .isEqualTo("NOT_IMPACTED");
        // l'agent ne s'est pas prononcé sur demo-front : le module reste affiché
        assertThat(detail.getString(analysis + ".modules.find { it.name == 'demo-front' }.qualification"))
                .isEqualTo("UNQUALIFIED");
        assertThat(detail.getString(analysis + ".modules.find { it.name == 'demo-front' }.reasons[0].type"))
                .isEqualTo("TERM");
        assertThat(detail.getString(analysis + ".roots[0].label")).isEqualTo("demo-rv");
        assertThat(detail.getList("messages[1].sources.kind")).containsOnly("MODULE");
        api().delete("/api/conversations/" + id).then().statusCode(204);
    }
}
