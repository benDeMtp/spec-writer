package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CopilotCliVersionTest {

    @Test
    void versionLueDansLaSortieDuCli() {
        assertThat(CopilotCliVersion.parse("GitHub Copilot CLI 1.0.105.\nRun 'copilot update' to check for updates.\n"))
                .contains(new CopilotCliVersion(1, 0, 105));
        assertThat(CopilotCliVersion.parse("1.2.3-beta")).contains(new CopilotCliVersion(1, 2, 3));
        assertThat(CopilotCliVersion.parse("aucune version")).isEqualTo(Optional.empty());
        assertThat(CopilotCliVersion.parse(null)).isEqualTo(Optional.empty());
    }

    @Test
    void comparaisonNumeriqueEtNonLexicale() {
        CopilotCliVersion min = CopilotCliVersion.parse("1.0.105").orElseThrow();
        assertThat(new CopilotCliVersion(1, 0, 105).isAtLeast(min)).isTrue();
        assertThat(new CopilotCliVersion(1, 0, 106).isAtLeast(min)).isTrue();
        assertThat(new CopilotCliVersion(1, 1, 0).isAtLeast(min)).isTrue();
        assertThat(new CopilotCliVersion(2, 0, 0).isAtLeast(min)).isTrue();
        assertThat(new CopilotCliVersion(1, 0, 11).isAtLeast(min)).isFalse();
        assertThat(new CopilotCliVersion(0, 9, 999).isAtLeast(min)).isFalse();
        assertThat(min).hasToString("1.0.105");
    }

    @Test
    void cliTropAncienRefuseAvecLaVersionTrouveeEtLaCommandeDeMiseAJour() {
        DomainException e = catchThrowableOfType(DomainException.class,
                () -> CopilotCliVersion.require("GitHub Copilot CLI 1.0.11.", "1.0.105"));
        assertThat(e.code()).isEqualTo(ErrorCode.COPILOT_CLI_OUTDATED);
        assertThat(e.userMessage()).contains("1.0.11").contains("1.0.105").contains("copilot update");
    }

    @Test
    void versionSuffisanteOuIllisibleAcceptee() {
        assertThatCode(() -> CopilotCliVersion.require("GitHub Copilot CLI 1.0.105.", "1.0.105")).doesNotThrowAnyException();
        // sortie inattendue : le démarrage du CLI tranchera
        assertThatCode(() -> CopilotCliVersion.require("", "1.0.105")).doesNotThrowAnyException();
    }
}
