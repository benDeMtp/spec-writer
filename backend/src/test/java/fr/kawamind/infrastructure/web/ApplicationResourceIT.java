package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static fr.kawamind.support.LocalApi.origin;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.empty;

import fr.kawamind.application.lifecycle.GracefulStop;
import fr.kawamind.application.lifecycle.RunningWork;
import fr.kawamind.domain.port.ClockPort;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** T015 (019) : état de l'outil et Quitter, contracts/rest-api.md. Le processus n'est pas arrêté. */
@QuarkusTest
@Isolated
class ApplicationResourceIT {

    @Inject
    RunningWork running;

    @Inject
    ClockPort clock;

    final List<Integer> exits = new CopyOnWriteArrayList<>();

    @BeforeEach
    void simulerLArret() {
        QuarkusMock.installMockForType(new GracefulStop(running, exits::add, clock, d -> { }), GracefulStop.class);
    }

    @Test
    void etatDeLOutil() {
        api().get("/api/application").then().statusCode(200)
                .body("os", anyOf(equalTo("MACOS"), equalTo("WINDOWS"), equalTo("LINUX")))
                .body("installation", equalTo("SOURCES"))
                .body("work", empty());
    }

    @Test
    void quitterRepond202PuisArreteUneSeuleFois() throws InterruptedException {
        api().body("{}").post("/api/application/quit").then().statusCode(202).body("stopping", equalTo(true));
        api().body("{}").post("/api/application/quit").then().statusCode(202).body("stopping", equalTo(true));

        for (int i = 0; i < 50 && exits.isEmpty(); i++) {
            Thread.sleep(20);
        }
        assertThat(exits).containsExactly(0);
    }

    @Test
    void sansSessionRefuse() {
        given().header("Origin", origin()).contentType("application/json").body("{}")
                .post("/api/application/quit").then().statusCode(403);
    }

    @Test
    void sansJsonRefuse() {
        api().contentType("text/plain").body("x").post("/api/application/quit").then().statusCode(415);
    }
}
