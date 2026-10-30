package dev.specwriter.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageDetectorTest {

    @Test
    void francais() {
        assertThat(LanguageDetector.detect("Les employés doivent pouvoir réserver une salle de réunion et être "
                + "prévenus quand elle est déjà prise.")).isEqualTo(Language.FR);
    }

    @Test
    void anglais() {
        assertThat(LanguageDetector.detect("Employees should be able to book a meeting room for a given time and "
                + "be notified when it is already taken.")).isEqualTo(Language.EN);
    }

    @Test
    void casAmbiguEnFrancaisParDefaut() {
        assertThat(LanguageDetector.detect("API REST CRUD JSON Kafka Kubernetes")).isEqualTo(Language.FR);
        assertThat(LanguageDetector.detect("")).isEqualTo(Language.FR);
    }
}
