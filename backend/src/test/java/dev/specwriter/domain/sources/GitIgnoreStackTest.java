package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitIgnoreStackTest {

    @Test
    void sansRegleRienNEstIgnore() {
        assertThat(GitIgnoreStack.EMPTY.ignored("a/b.java", false)).isFalse();
    }

    @Test
    void leGitignoreLePlusProfondLEmporte() {
        GitIgnoreStack stack = GitIgnoreStack.EMPTY.push(GitIgnoreRules.parse("", "*.txt\n"))
                .push(GitIgnoreRules.parse("docs", "!lisez-moi.txt\n"));
        assertThat(stack.ignored("a.txt", false)).isTrue();
        assertThat(stack.ignored("docs/autre.txt", false)).isTrue();
        assertThat(stack.ignored("docs/lisez-moi.txt", false)).isFalse();
    }

    @Test
    void laNegationNeSortPasUnFichierDUnDossierIgnore() {
        // comme T002 : « generated/ » puis « !keep.txt » ; le dossier est coupé, keep.txt n'est jamais vu
        GitIgnoreStack stack = GitIgnoreStack.EMPTY.push(GitIgnoreRules.parse("", "generated/\n!keep.txt\n"));
        assertThat(stack.ignored("modules/relance/generated", true)).isTrue();
        assertThat(stack.ignored("modules/relance/keep.txt", false)).isFalse();
    }

    @Test
    void unGitignoreVideNeChangeRien() {
        GitIgnoreStack stack = GitIgnoreStack.EMPTY.push(GitIgnoreRules.parse("", "\n# rien\n"));
        assertThat(stack).isSameAs(GitIgnoreStack.EMPTY);
    }
}
