package fr.kawamind.infrastructure.llm;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Outils de la suite d'évaluation (014, research R7) qui touchent au paquet LLM : schéma strict comme
 * les appels de l'application, version du Copilot CLI utilisé. Les modèles, eux, sont les beans de
 * l'application ({@code EvalProfile}).
 */
public final class EvalTools {

    private EvalTools() {
    }

    /** Schéma JSON strict, comme pour les appels de l'application. */
    public static Map<String, Object> strict(Map<String, Object> schema) {
        return StrictJsonSchema.strict(schema);
    }

    /** Version du Copilot CLI utilisé, telle qu'il l'affiche ; « embarqué » s'il vient du SDK. */
    public static String cliVersion(Optional<String> cliPath) {
        return switch (CopilotCliLocator.fromSystem().resolve(cliPath)) {
            case CopilotCliLocator.Resolution.Bundled _ -> "embarqué";
            case CopilotCliLocator.Resolution.External(Path cli) -> CopilotCliVersion.parse(
                    CopilotRuntime.versionOutput(cli, CopilotCliLocator.childPath(System.getenv(), cli,
                            System.getProperty("os.name", "").startsWith("Windows"))))
                    .map(CopilotCliVersion::toString).orElse("inconnue");
            case CopilotCliLocator.Resolution.Missing _ -> "introuvable";
        };
    }
}
