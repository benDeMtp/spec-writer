package fr.kawamind.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.support.InMemoryConsents;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Aucun envoi à un service tiers sans accord en vigueur (FR-024). */
class ConsentPolicyTest {

    private final InMemoryConsents consents = new InMemoryConsents();
    private final ConsentPolicy policy = new ConsentPolicy(consents);

    @Test
    void sansAccordLEnvoiEstRefuseAvecUnMessageParService() {
        assertThat(policy.isGranted(ConsentService.JIRA)).isFalse();
        assertThatThrownBy(() -> policy.requireGranted(ConsentService.JIRA))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED);
                    assertThat(e.userMessage()).contains("Jira");
                });
        assertThatThrownBy(() -> policy.requireGranted(ConsentService.COPILOT))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.userMessage()).contains("GitHub Copilot"));
    }

    @Test
    void leDernierChoixFaitFoi() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        policy.requireGranted(ConsentService.COPILOT);
        consents.record(new ConsentRecord(ConsentService.COPILOT, false, Instant.EPOCH.plusSeconds(1)));
        assertThat(policy.isGranted(ConsentService.COPILOT)).isFalse();
        assertThat(policy.isGranted(ConsentService.JIRA)).isFalse();
    }
}
