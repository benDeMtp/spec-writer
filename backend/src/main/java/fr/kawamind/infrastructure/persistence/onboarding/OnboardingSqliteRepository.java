package fr.kawamind.infrastructure.persistence.onboarding;

import fr.kawamind.domain.onboarding.Onboarding;
import fr.kawamind.domain.onboarding.OnboardingStep;
import fr.kawamind.domain.onboarding.StepState;
import fr.kawamind.domain.port.OnboardingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Tables {@code onboarding} et {@code onboarding_step} (V29, 018). */
@ApplicationScoped
public class OnboardingSqliteRepository implements OnboardingRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Onboarding load() {
        List<?> rows = em.createNativeQuery("select current_step, completed_at from onboarding where id = 1")
                .getResultList();
        if (rows.isEmpty()) {
            return Onboarding.initial();
        }
        Object[] row = (Object[]) rows.getFirst();
        Map<OnboardingStep, StepState> states = new EnumMap<>(OnboardingStep.class);
        for (Object r : em.createNativeQuery("select step, state from onboarding_step").getResultList()) {
            Object[] s = (Object[]) r;
            states.put(OnboardingStep.valueOf((String) s[0]), StepState.valueOf((String) s[1]));
        }
        return new Onboarding(OnboardingStep.valueOf((String) row[0]), states,
                row[1] == null ? null : Instant.parse((String) row[1]));
    }

    @Override
    @Transactional
    public void save(Onboarding onboarding) {
        // les nulls sont écrits en littéraux : un null typé est mal passé aux requêtes natives
        String completed = onboarding.completedAt() == null ? "null" : "?2";
        var query = em.createNativeQuery("insert into onboarding (id, current_step, completed_at, updated_at) "
                + "values (1, ?1, " + completed + ", strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) on conflict (id) do update set current_step = "
                + "excluded.current_step, completed_at = excluded.completed_at, updated_at = excluded.updated_at")
                .setParameter(1, onboarding.currentStep().name());
        if (onboarding.completedAt() != null) {
            query.setParameter(2, onboarding.completedAt().toString());
        }
        query.executeUpdate();
        em.createNativeQuery("delete from onboarding_step").executeUpdate();
        onboarding.states().forEach((step, state) -> em
                .createNativeQuery("insert into onboarding_step (step, state) values (?1, ?2)")
                .setParameter(1, step.name()).setParameter(2, state.name()).executeUpdate());
    }
}
