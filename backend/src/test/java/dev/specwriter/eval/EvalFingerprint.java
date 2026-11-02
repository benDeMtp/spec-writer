package dev.specwriter.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Empreinte de ce qui influence les sorties du LLM (T137, T138). Même calcul que
 * {@code ci/check-eval.sh} : une ligne {@code <sha256>  <chemin>} par fichier, chemins relatifs à la
 * racine du dépôt triés octet par octet (fichiers cachés exclus), puis la version du Copilot SDK et
 * le modèle par défaut de l'application ; l'empreinte est le SHA-256 de ce manifeste.
 */
public final class EvalFingerprint {

    /** Dossiers couverts, relatifs à la racine du dépôt. */
    public static final List<String> SOURCES = List.of(
            "backend/src/main/resources/prompts",
            "backend/src/main/java/dev/specwriter/infrastructure/llm",
            "backend/src/test/resources/golden",
            "backend/src/test/resources/eval/prompts",
            "backend/src/test/java/dev/specwriter/eval");

    private static final Pattern SDK = Pattern.compile("<copilot-sdk\\.version>([^<]*)</copilot-sdk\\.version>");
    private static final Pattern MODEL = Pattern.compile("(?m)^specwriter\\.copilot\\.model=(.*)$");

    private EvalFingerprint() {
    }

    public static String compute(Path repoRoot) {
        return sha256(manifest(repoRoot).getBytes(StandardCharsets.UTF_8));
    }

    static String manifest(Path repoRoot) {
        StringBuilder out = new StringBuilder();
        for (String file : files(repoRoot)) {
            out.append(sha256(read(repoRoot.resolve(file)))).append("  ").append(file).append('\n');
        }
        out.append("copilot-sdk=").append(copilotSdk(repoRoot)).append('\n');
        out.append("default-model=").append(defaultModel(repoRoot)).append('\n');
        return out.toString();
    }

    public static String copilotSdk(Path repoRoot) {
        Matcher m = SDK.matcher(new String(read(repoRoot.resolve("backend/pom.xml")), StandardCharsets.UTF_8));
        return m.find() ? m.group(1).strip() : "";
    }

    /** Modèle de {@code specwriter.copilot.model}, hors profil ; vide pour le modèle par défaut du compte. */
    public static String defaultModel(Path repoRoot) {
        Matcher m = MODEL.matcher(new String(read(repoRoot.resolve("backend/src/main/resources/application.properties")),
                StandardCharsets.UTF_8));
        return m.find() ? m.group(1).strip() : "";
    }

    private static List<String> files(Path repoRoot) {
        return SOURCES.stream().map(repoRoot::resolve).filter(Files::isDirectory).flatMap(dir -> {
            try (Stream<Path> walk = Files.walk(dir)) {
                return walk.filter(Files::isRegularFile)
                        .filter(p -> !hidden(repoRoot.relativize(p)))
                        .map(p -> repoRoot.relativize(p).toString().replace('\\', '/'))
                        .toList().stream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).sorted(EvalFingerprint::compareBytes).toList();
    }

    private static boolean hidden(Path relative) {
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static int compareBytes(String a, String b) {
        return java.util.Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
