package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import fr.kawamind.domain.onboarding.Onboarding;
import fr.kawamind.domain.onboarding.OnboardingStep;
import fr.kawamind.domain.onboarding.StepState;
import fr.kawamind.domain.port.OnboardingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T011, T012, T049 (018) : progression de l'assistant, en base et par {@code /api/onboarding}
 * (contracts/rest-api.md). La progression est globale : elle est remise à son état d'origine.
 */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class OnboardingResourceIT {

    @Inject
    OnboardingRepository repository;

    Onboarding before;

    @BeforeEach
    void garder() {
        before = repository.load();
        repository.save(Onboarding.initial());
    }

    @AfterEach
    void remettre() {
        repository.save(before);
    }

    @Test
    void depotAllerRetour() {
        Onboarding o = Onboarding.initial().markDone(OnboardingStep.COPILOT).skip(OnboardingStep.JIRA);
        repository.save(o);
        Onboarding read = repository.load();
        assertThat(read.currentStep()).isEqualTo(OnboardingStep.CONFLUENCE);
        assertThat(read.state(OnboardingStep.COPILOT)).isEqualTo(StepState.DONE);
        assertThat(read.state(OnboardingStep.JIRA)).isEqualTo(StepState.SKIPPED);
        assertThat(read.completedAt()).isNull();
        repository.save(read.complete(java.time.Instant.parse("2026-11-20T08:00:00Z")));
        assertThat(repository.load().completedAt()).isEqualTo(java.time.Instant.parse("2026-11-20T08:00:00Z"));
    }

    @Test
    void progressionEtEtapes() {
        api().get("/api/onboarding").then().statusCode(200).body("currentStep", equalTo("WELCOME"))
                .body("completed", equalTo(false)).body("steps.size()", equalTo(6))
                .body("steps[0].step", equalTo("COPILOT")).body("steps[0].required", equalTo(true))
                .body("steps[1].state", equalTo("TODO")).body("autoOpen", equalTo(true));
        api().body(Map.of("state", "SKIPPED")).put("/api/onboarding/steps/COPILOT").then().statusCode(409)
                .body("code", equalTo("ONBOARDING_COPILOT_REQUIRED"));
        // « COPILOT fait » dépend de l'état réel de Copilot, partagé entre IT : couvert par ManageOnboardingTest
        api().body(Map.of("state", "SKIPPED")).put("/api/onboarding/steps/WELCOME").then().statusCode(400)
                .body("code", equalTo("ONBOARDING_UNKNOWN_STEP"));
        api().body(Map.of("step", "JIRA")).put("/api/onboarding/current").then().statusCode(409);
        api().post("/api/onboarding/complete").then().statusCode(409);

        // Copilot fait (son contrôle réel est couvert par ManageOnboardingTest)
        repository.save(Onboarding.initial().markDone(OnboardingStep.COPILOT));
        api().body(Map.of("state", "SKIPPED")).put("/api/onboarding/steps/JIRA").then().statusCode(200)
                .body("currentStep", equalTo("CONFLUENCE")).body("steps[1].state", equalTo("SKIPPED"));
        api().body(Map.of("step", "JIRA")).put("/api/onboarding/current").then().statusCode(200)
                .body("currentStep", equalTo("JIRA"));
        String at = api().post("/api/onboarding/complete").then().statusCode(200).body("completed", equalTo(true))
                .body("currentStep", equalTo("DONE")).body("autoOpen", equalTo(false))
                .body("completedAt", notNullValue()).extract().path("completedAt");
        api().post("/api/onboarding/complete").then().statusCode(200).body("completedAt", equalTo(at));
    }

    @Test
    void protegeeCommeLeResteDeLApi() {
        given().get("/api/onboarding").then().statusCode(403);
        given().contentType("application/json").body(Map.of("state", "SKIPPED")).put("/api/onboarding/steps/JIRA")
                .then().statusCode(403);
    }

    @Test
    void leJournalNeNommeQueLesEtapes() throws Exception {
        repository.save(Onboarding.initial().markDone(OnboardingStep.COPILOT));
        api().body(Map.of("state", "SKIPPED")).put("/api/onboarding/steps/JIRA").then().statusCode(200);
        Path log = Path.of("target/test-logs/spec-writer.log");
        assertThat(Files.readString(log)).contains("Assistant de premier lancement : étape JIRA passée");
    }
}
