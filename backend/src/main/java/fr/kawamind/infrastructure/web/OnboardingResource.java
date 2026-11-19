package fr.kawamind.infrastructure.web;

import fr.kawamind.application.onboarding.ManageOnboarding;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.onboarding.Onboarding;
import fr.kawamind.domain.onboarding.OnboardingStep;
import fr.kawamind.domain.onboarding.StepState;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;

/** {@code /api/onboarding} : progression de l'assistant de premier lancement (018, contracts/rest-api.md). */
@Path("/api/onboarding")
@Produces(MediaType.APPLICATION_JSON)
public class OnboardingResource {

    public record StepView(String step, boolean required, String state) {
    }

    public record OnboardingView(String currentStep, List<StepView> steps, boolean completed, Instant completedAt,
            boolean autoOpen) {
    }

    public record StateInput(String state) {
    }

    public record StepInput(String step) {
    }

    @Inject
    ManageOnboarding manage;

    @GET
    public OnboardingView get() {
        return view(manage.state());
    }

    @PUT
    @Path("/steps/{step}")
    @Consumes(MediaType.APPLICATION_JSON)
    public OnboardingView step(@PathParam("step") String step, StateInput input) {
        OnboardingStep s = parse(step);
        String state = input == null ? null : input.state();
        if (StepState.DONE.name().equals(state)) {
            return view(manage.markDone(s));
        }
        if (StepState.SKIPPED.name().equals(state)) {
            return view(manage.skip(s));
        }
        throw new DomainException(ErrorCode.VALIDATION_FAILED);
    }

    @PUT
    @Path("/current")
    @Consumes(MediaType.APPLICATION_JSON)
    public OnboardingView current(StepInput input) {
        return view(manage.goTo(parse(input == null ? null : input.step())));
    }

    @POST
    @Path("/complete")
    public OnboardingView complete() {
        return view(manage.complete());
    }

    private static OnboardingStep parse(String step) {
        if (step == null) {
            throw new DomainException(ErrorCode.ONBOARDING_UNKNOWN_STEP);
        }
        try {
            return OnboardingStep.valueOf(step);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.ONBOARDING_UNKNOWN_STEP);
        }
    }

    private static OnboardingView view(ManageOnboarding.View v) {
        Onboarding o = v.onboarding();
        List<StepView> steps = OnboardingStep.tracked().stream()
                .map(s -> new StepView(s.name(), s.required(), o.state(s).name())).toList();
        return new OnboardingView(o.currentStep().name(), steps, o.completed(), o.completedAt(), v.autoOpen());
    }
}
