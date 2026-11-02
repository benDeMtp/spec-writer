package dev.specwriter.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void codesReconnusSansCasse() {
        assertThat(Language.fromCode("FR")).isEqualTo(Language.FR);
        assertThat(Language.fromCode("en")).isEqualTo(Language.EN);
        assertThat(Language.EN.code()).isEqualTo("en");
    }

    @Test
    void langueInconnueRefusee() {
        assertThatThrownBy(() -> Language.fromCode("de")).isInstanceOf(IllegalArgumentException.class);
    }
}
