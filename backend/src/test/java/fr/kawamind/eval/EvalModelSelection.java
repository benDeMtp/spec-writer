package fr.kawamind.eval;

import fr.kawamind.infrastructure.llm.ModelSelection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Modèle de l'évaluation ({@code eval.model}), sinon celui du compte ; actif dans {@link EvalProfile} seulement. */
@Alternative
@ApplicationScoped
public class EvalModelSelection implements ModelSelection {

    @ConfigProperty(name = "eval.model")
    Optional<String> model = Optional.empty();

    @Override
    public Optional<String> currentModel() {
        return model.filter(m -> !m.isBlank());
    }
}
