package fr.kawamind.eval;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Profil de l'évaluation réelle (014, research R7) : l'application telle qu'elle est livrée (AI
 * Services, garde-fous, plafonds), mais sans rejeu, sur le vrai Copilot, avec le {@code COPILOT_HOME}
 * de l'utilisateur ({@code copilot login}) et le modèle de l'évaluation, quel que soit celui des
 * réglages de la base de test ({@link EvalModelSelection}).
 *
 * <p>Propriétés : {@code eval.copilot-home} ({@code ~/.copilot} par défaut), {@code eval.model} (sinon
 * le modèle par défaut du compte), {@code specwriter.copilot.cli-path}.
 */
public class EvalProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return overrides(System.getProperty("eval.copilot-home", System.getProperty("user.home") + "/.copilot"),
                System.getProperty("eval.model", ""));
    }

    static Map<String, String> overrides(String copilotHome, String model) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("specwriter.llm.replay-dir", "");
        out.put("specwriter.copilot.home", copilotHome);
        out.put("eval.model", model);
        return out;
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(EvalModelSelection.class);
    }
}
