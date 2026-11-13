package dev.specwriter.domain.sources;

import static dev.specwriter.domain.sources.GitIgnoreRules.Match.IGNORED;
import static dev.specwriter.domain.sources.GitIgnoreRules.Match.INCLUDED;
import static dev.specwriter.domain.sources.GitIgnoreRules.Match.NONE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitIgnoreRulesTest {

    @Test
    void unMotifSansBarreSAppliqueAuNomATouteProfondeur() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "*.log\ngenerated\n");
        assertThat(rules.match("a.log", false)).isEqualTo(IGNORED);
        assertThat(rules.match("x/y/b.log", false)).isEqualTo(IGNORED);
        assertThat(rules.match("x/generated", true)).isEqualTo(IGNORED);
        assertThat(rules.match("x/generated.java", false)).isEqualTo(NONE);
    }

    @Test
    void lesCommentairesLignesVidesEtEchappements() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "# commentaire\n\n\\#fichier\n\\!important\n   \n");
        assertThat(rules.match("#fichier", false)).isEqualTo(IGNORED);
        assertThat(rules.match("!important", false)).isEqualTo(IGNORED);
        assertThat(rules.match("commentaire", false)).isEqualTo(NONE);
    }

    @Test
    void uneBarreEnTeteOuAuMilieuAncreLeMotif() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "/build.txt\ndoc/frotz\n");
        assertThat(rules.match("build.txt", false)).isEqualTo(IGNORED);
        assertThat(rules.match("sous/build.txt", false)).isEqualTo(NONE);
        assertThat(rules.match("doc/frotz", true)).isEqualTo(IGNORED);
        assertThat(rules.match("a/doc/frotz", true)).isEqualTo(NONE);
    }

    @Test
    void uneBarreFinaleNeVautQuePourUnDossier() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "generated/\n");
        assertThat(rules.match("modules/relance/generated", true)).isEqualTo(IGNORED);
        assertThat(rules.match("modules/relance/generated", false)).isEqualTo(NONE);
    }

    @Test
    void lesDoublesEtoiles() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "**/logs\nabc/**\na/**/z\n");
        assertThat(rules.match("logs", true)).isEqualTo(IGNORED);
        assertThat(rules.match("x/y/logs", true)).isEqualTo(IGNORED);
        assertThat(rules.match("abc/d/e", false)).isEqualTo(IGNORED);
        assertThat(rules.match("abc", true)).isEqualTo(NONE);
        assertThat(rules.match("a/z", false)).isEqualTo(IGNORED);
        assertThat(rules.match("a/b/c/z", false)).isEqualTo(IGNORED);
    }

    @Test
    void etoileInterrogationEtClasses() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "fich?er.txt\n*.[ch]\nv[!0-9].md\n");
        assertThat(rules.match("fichier.txt", false)).isEqualTo(IGNORED);
        assertThat(rules.match("fich/er.txt", false)).isEqualTo(NONE);
        assertThat(rules.match("src/a.c", false)).isEqualTo(IGNORED);
        assertThat(rules.match("src/a.h", false)).isEqualTo(IGNORED);
        assertThat(rules.match("src/a.java", false)).isEqualTo(NONE);
        assertThat(rules.match("va.md", false)).isEqualTo(IGNORED);
        assertThat(rules.match("v1.md", false)).isEqualTo(NONE);
    }

    @Test
    void laDerniereRegleQuiCorrespondLEmporte() {
        GitIgnoreRules rules = GitIgnoreRules.parse("", "*.txt\n!garder.txt\n");
        assertThat(rules.match("jeter.txt", false)).isEqualTo(IGNORED);
        assertThat(rules.match("garder.txt", false)).isEqualTo(INCLUDED);
    }

    @Test
    void lesReglesDUnSousDossierNeValentQueSousLui() {
        GitIgnoreRules rules = GitIgnoreRules.parse("modules/relance", "/local.txt\n*.tmp\n");
        assertThat(rules.match("modules/relance/local.txt", false)).isEqualTo(IGNORED);
        assertThat(rules.match("modules/relance/src/x.tmp", false)).isEqualTo(IGNORED);
        assertThat(rules.match("local.txt", false)).isEqualTo(NONE);
        assertThat(rules.match("modules/relance-bis/x.tmp", false)).isEqualTo(NONE);
    }
}
