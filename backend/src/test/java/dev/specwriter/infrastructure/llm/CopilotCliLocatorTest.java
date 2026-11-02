package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.infrastructure.llm.CopilotCliLocator.Resolution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Copilot CLI de l'utilisateur, les installeurs n'embarquant plus le runtime (research.md R15). */
class CopilotCliLocatorTest {

    @TempDir
    Path tmp;

    private Path executable(String relative) throws IOException {
        Path file = tmp.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "#!/bin/sh\n");
        file.toFile().setExecutable(true);
        return file;
    }

    private CopilotCliLocator unix(Map<String, String> env, boolean bundled) {
        // aucun dossier système : le résultat ne dépend pas de la machine qui lance le test
        return new CopilotCliLocator(env, tmp.resolve("home"), false, bundled, List.of());
    }

    @Test
    void cheminExpliciteEnConfigurationPuisVariableDEnvironnement() throws IOException {
        Path configured = executable("ailleurs/copilot");
        Path fromEnv = executable("env/copilot");
        CopilotCliLocator locator = unix(Map.of("COPILOT_CLI_PATH", fromEnv.toString()), true);
        assertThat(locator.resolve(Optional.of(configured.toString()))).isEqualTo(new Resolution.External(configured));
        assertThat(locator.resolve(Optional.empty())).isEqualTo(new Resolution.External(fromEnv));
    }

    @Test
    void cheminExpliciteIntrouvableSignaleSansChercherAilleurs() throws IOException {
        executable("bin/copilot");
        CopilotCliLocator locator = unix(Map.of("PATH", tmp.resolve("bin").toString()), true);
        Resolution r = locator.resolve(Optional.of(tmp.resolve("absent/copilot").toString()));
        assertThat(r).isInstanceOf(Resolution.Missing.class);
        assertThat(((Resolution.Missing) r).searched()).containsExactly(tmp.resolve("absent/copilot"));
    }

    @Test
    void runtimeEmbarqueUtiliseQuandAucunCheminNEstImpose() throws IOException {
        executable("bin/copilot");
        CopilotCliLocator locator = unix(Map.of("PATH", tmp.resolve("bin").toString()), true);
        assertThat(locator.resolve(Optional.empty())).isEqualTo(new Resolution.Bundled());
    }

    @Test
    void sansRuntimeEmbarqueLePathPuisLesDossiersDInstallationHabituels() throws IOException {
        Path inPath = executable("bin/copilot");
        CopilotCliLocator locator = unix(Map.of("PATH", tmp.resolve("vide") + ":" + tmp.resolve("bin")), false);
        assertThat(locator.resolve(Optional.empty())).isEqualTo(new Resolution.External(inPath));

        // lancé depuis le Finder, le PATH est minimal : ~/.local/bin et les autres dossiers connus sont examinés
        Path local = executable("home/.local/bin/copilot");
        assertThat(unix(Map.of("PATH", "/usr/bin:/bin"), false).resolve(Optional.empty()))
                .isEqualTo(new Resolution.External(local));
    }

    @Test
    void fichierNonExecutableIgnoreEtAbsenceSignaleeAvecLesDossiersExamines() throws IOException {
        Path file = tmp.resolve("bin/copilot");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        file.toFile().setExecutable(false);
        Resolution r = unix(Map.of("PATH", tmp.resolve("bin").toString()), false).resolve(Optional.empty());
        assertThat(r).isInstanceOf(Resolution.Missing.class);
        assertThat(((Resolution.Missing) r).searched()).contains(tmp.resolve("bin/copilot"), tmp.resolve("home/.local/bin/copilot"));
    }

    @Test
    void windowsCherchePointExeEtPointCmdDansLesDossiersDeWingetEtDeNpm() throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("Path", tmp.resolve("vide").toString());
        env.put("APPDATA", tmp.resolve("Roaming").toString());
        env.put("LOCALAPPDATA", tmp.resolve("Local").toString());
        Path npm = executable("Roaming/npm/copilot.cmd");
        CopilotCliLocator windows = new CopilotCliLocator(env, tmp.resolve("home"), true, false, List.of());
        assertThat(windows.resolve(Optional.empty())).isEqualTo(new Resolution.External(npm));
        Path winget = executable("Local/Microsoft/WinGet/Links/copilot.exe");
        assertThat(windows.resolve(Optional.empty())).isEqualTo(new Resolution.External(winget));
    }

    @Test
    void dossiersSystemeEtPlateformeDuRuntimeEmbarque() {
        assertThat(CopilotCliLocator.systemDirs(false)).contains(Path.of("/opt/homebrew/bin"), Path.of("/usr/local/bin"));
        assertThat(CopilotCliLocator.systemDirs(true)).isEmpty();
        assertThat(CopilotCliLocator.platform("Mac OS X", "aarch64")).isEqualTo("darwin-arm64");
        assertThat(CopilotCliLocator.platform("Mac OS X", "x86_64")).isEqualTo("darwin-x64");
        assertThat(CopilotCliLocator.platform("Linux", "amd64")).isEqualTo("linux-x64");
        assertThat(CopilotCliLocator.platform("Windows 11", "amd64")).isEqualTo("win32-x64");
    }

    @Test
    void lePathDuCliCommenceParSonDossierPourTrouverNode() {
        Path cli = tmp.resolve("node/bin/copilot");
        Map<String, String> env = CopilotCliLocator.childPath(Map.of("PATH", "/usr/bin:/bin", "HOME", "/h"), cli, false);
        assertThat(env).containsEntry("PATH", tmp.resolve("node/bin") + ":/usr/bin:/bin").containsEntry("HOME", "/h");
        Map<String, String> win = CopilotCliLocator.childPath(Map.of("Path", "C:\\Windows"), cli, true);
        assertThat(win).containsEntry("Path", tmp.resolve("node/bin") + ";C:\\Windows").doesNotContainKey("PATH");
        assertThat(CopilotCliLocator.childPath(Map.of(), cli, false)).containsEntry("PATH", tmp.resolve("node/bin").toString());
    }
}
