package dev.specwriter.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorAction;
import dev.specwriter.domain.error.ErrorCode;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class ProblemMapperTest {

    private final ProblemMapper mapper = new ProblemMapper();

    @Test
    void domainExceptionDevientUnProblem() {
        Response r = mapper.domain(new DomainException(ErrorCode.NEED_TOO_SHORT));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getMediaType().toString()).isEqualTo("application/problem+json");
        Problem p = (Problem) r.getEntity();
        assertThat(p.code()).isEqualTo("NEED_TOO_SHORT");
        assertThat(p.action()).isEqualTo(ErrorAction.EDIT_INPUT);
        assertThat(p.userMessage()).contains("10 mots");
    }

    @Test
    void exceptionInattendueGeneriqueSansDetail() {
        Response r = mapper.unexpected(new IllegalStateException("détail interne SECRET"));
        Problem p = (Problem) r.getEntity();
        assertThat(r.getStatus()).isEqualTo(500);
        assertThat(p.userMessage()).isEqualTo("Une erreur inattendue est survenue, réessayez.");
        assertThat(p.toString()).doesNotContain("SECRET").doesNotContain("IllegalState");
    }

    @Test
    void chaqueCodeAUnMessageEtUneAction() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank();
            assertThat(code.action()).as(code.name()).isNotNull();
        }
    }
}
