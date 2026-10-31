package dev.specwriter.infrastructure.llm;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version du Copilot CLI, lue dans la sortie de {@code copilot --version}. */
record CopilotCliVersion(int major, int minor, int patch) {

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    static Optional<CopilotCliVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = VERSION.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(new CopilotCliVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3))));
    }

    /**
     * Refuse un CLI plus ancien que {@code minimum} : le SDK lui parle un protocole qu'il ne connaît pas
     * encore (« Unhandled method connect » avec le CLI 1.0.11). Une sortie illisible est acceptée.
     */
    static void require(String versionOutput, String minimum) {
        Optional<CopilotCliVersion> found = parse(versionOutput);
        CopilotCliVersion min = parse(minimum).orElseThrow(() -> new IllegalArgumentException("Version minimale invalide"));
        if (found.isPresent() && !found.get().isAtLeast(min)) {
            throw new DomainException(ErrorCode.COPILOT_CLI_OUTDATED, "Le Copilot CLI installé (" + found.get()
                    + ") est trop ancien : la version " + min + " ou plus récente est requise. Lancez « copilot update », "
                    + "puis relancez spec-writer.");
        }
    }

    boolean isAtLeast(CopilotCliVersion other) {
        if (major != other.major) {
            return major > other.major;
        }
        if (minor != other.minor) {
            return minor > other.minor;
        }
        return patch >= other.patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
