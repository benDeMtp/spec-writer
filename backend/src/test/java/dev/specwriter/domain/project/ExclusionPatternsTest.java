package dev.specwriter.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 006, US2 : motifs d'exclusion d'un dossier de documents (research.md R4). */
class ExclusionPatternsTest {

    static ExclusionPatterns of(String... patterns) {
        return ExclusionPatterns.of(List.of(patterns));
    }

    @Test
    void motifSansBarreSurLeNomATouteProfondeur() {
        ExclusionPatterns p = of("*.xlsx", "brouillon*");
        assertThat(p.excludes("Suivi.xlsx")).isTrue();
        assertThat(p.excludes("Specs/2026/Suivi.XLSX")).isTrue();
        assertThat(p.excludes("Specs/brouillon-v2.docx")).isTrue();
        assertThat(p.excludes("Specs/Aurora.docx")).isFalse();
        assertThat(p.excludes("xlsx/Aurora.docx")).isFalse();
    }

    @Test
    void motifAvecBarreDepuisLaRacine() {
        ExclusionPatterns p = of("Archives/**", "Specs/*.pdf");
        assertThat(p.excludes("Archives/2023.pdf")).isTrue();
        assertThat(p.excludes("archives/2023/bilan.pdf")).isTrue();
        assertThat(p.excludes("Specs/Archives/2023.pdf")).isFalse();
        assertThat(p.excludes("Specs/plan.pdf")).isTrue();
        assertThat(p.excludes("Specs/2026/plan.pdf")).isFalse();
    }

    @Test
    void doubleEtoileSurPlusieursNiveaux() {
        ExclusionPatterns p = of("**/brouillons/**", "Specs/**/*.pdf");
        assertThat(p.excludes("brouillons/a.docx")).isTrue();
        assertThat(p.excludes("Specs/x/brouillons/y/a.docx")).isTrue();
        assertThat(p.excludes("Specs/plan.pdf")).isTrue();
        assertThat(p.excludes("Specs/a/b/plan.pdf")).isTrue();
        assertThat(p.excludes("Autres/plan.pdf")).isFalse();
    }

    @Test
    void pointDInterrogationClassesEtDossiers() {
        ExclusionPatterns p = of("v?.docx", "[ab]*.pdf", "[!c]x.html", "tmp/");
        assertThat(p.excludes("v1.docx")).isTrue();
        assertThat(p.excludes("v10.docx")).isFalse();
        assertThat(p.excludes("annexe.pdf")).isTrue();
        assertThat(p.excludes("contrat.pdf")).isFalse();
        assertThat(p.excludes("dx.html")).isTrue();
        assertThat(p.excludes("cx.html")).isFalse();
        assertThat(p.excludes("Specs/tmp/a.docx")).isTrue();
        assertThat(p.excludes("Specs/tmp.docx")).isFalse();
    }

    @Test
    void caracteresSpeciauxPrisLitteralement() {
        ExclusionPatterns p = of("Plan (v2).docx", "a+b.pdf");
        assertThat(p.excludes("Plan (v2).docx")).isTrue();
        assertThat(p.excludes("a+b.pdf")).isTrue();
        assertThat(p.excludes("aab.pdf")).isFalse();
    }

    @Test
    void lignesVidesIgnoreesSeparateurWindowsAccepte() {
        ExclusionPatterns p = of("  ", "Archives\\**", "");
        assertThat(p.patterns()).containsExactly("Archives/**");
        assertThat(p.excludes("Archives\\2023.pdf")).isTrue();
        assertThat(ExclusionPatterns.NONE.excludes("a.pdf")).isFalse();
    }

    @Test
    void egalesParLeursMotifs() {
        assertThat(of("*.pdf", "Archives/**")).isEqualTo(of(" *.pdf", "Archives/**", ""))
                .hasSameHashCodeAs(of("*.pdf", "Archives/**")).isNotEqualTo(of("*.pdf"));
    }

    @Test
    void fichiersToujoursIgnores() {
        assertThat(ExclusionPatterns.alwaysIgnored("~$Aurora.docx")).isTrue();
        assertThat(ExclusionPatterns.alwaysIgnored("Specs/.~lock.Aurora.docx#")).isTrue();
        assertThat(ExclusionPatterns.alwaysIgnored(".DS_Store")).isTrue();
        assertThat(ExclusionPatterns.alwaysIgnored(".git/config")).isTrue();
        assertThat(ExclusionPatterns.alwaysIgnored("Specs/Aurora.docx")).isFalse();
    }

    @Test
    void motifsRefusesAvecLeMotifEtLaRaison() {
        refused("/Archives/**", "/Archives/**", "relatif");
        refused("C:\\docs\\*.pdf", "C:/docs/*.pdf", "relatif");
        refused("../autre/**", "../autre/**", "..");
        refused("[abc.pdf", "[abc.pdf", "crochet");
        refused("x".repeat(201), "xxxxxxxx", "200");
        assertThatThrownBy(() -> ExclusionPatterns.of(Collections.nCopies(51, "*.pdf")))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.userMessage()).contains("50");
                });
    }

    static void refused(String pattern, String quoted, String reason) {
        assertThatThrownBy(() -> of(pattern)).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.userMessage()).contains(quoted).contains(reason);
        });
    }
}
