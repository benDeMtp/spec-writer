package dev.specwriter.infrastructure.architecture;

import java.io.Reader;
import java.util.List;
import java.util.function.Supplier;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

/**
 * Moteur de script qui n'exécute rien : l'analyseur Structurizr confie chaque {@code !script} à
 * {@code javax.script}, et ce moteur, déclaré pour Groovy, Kotlin, JavaScript et Ruby, le laisse
 * passer sans l'exécuter. Il n'agit que pendant une lecture de {@link StructurizrReader} : partout
 * ailleurs, il refuse.
 */
public final class IgnoredScripts {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private IgnoredScripts() {
    }

    /** Lecture pendant laquelle les scripts sont ignorés. */
    static <T> T during(Supplier<T> read) {
        ACTIVE.set(true);
        try {
            return read.get();
        } finally {
            ACTIVE.remove();
        }
    }

    /** Fabrique déclarée dans {@code META-INF/services/javax.script.ScriptEngineFactory}. */
    public static final class Factory implements ScriptEngineFactory {

        @Override
        public String getEngineName() {
            return "spec-writer : scripts ignorés";
        }

        @Override
        public String getEngineVersion() {
            return "1";
        }

        @Override
        public List<String> getExtensions() {
            return List.of("groovy", "kts", "js", "rb");
        }

        @Override
        public List<String> getMimeTypes() {
            return List.of();
        }

        @Override
        public List<String> getNames() {
            return List.of("spec-writer-ignored-scripts");
        }

        @Override
        public String getLanguageName() {
            return "aucun";
        }

        @Override
        public String getLanguageVersion() {
            return "1";
        }

        @Override
        public Object getParameter(String key) {
            return switch (key) {
                case ScriptEngine.ENGINE -> getEngineName();
                case ScriptEngine.ENGINE_VERSION -> getEngineVersion();
                case ScriptEngine.NAME -> getNames().getFirst();
                case ScriptEngine.LANGUAGE -> getLanguageName();
                case ScriptEngine.LANGUAGE_VERSION -> getLanguageVersion();
                default -> null;
            };
        }

        @Override
        public String getMethodCallSyntax(String obj, String m, String... args) {
            return "";
        }

        @Override
        public String getOutputStatement(String toDisplay) {
            return "";
        }

        @Override
        public String getProgram(String... statements) {
            return "";
        }

        @Override
        public ScriptEngine getScriptEngine() {
            return new Engine(this);
        }
    }

    private static final class Engine extends AbstractScriptEngine {

        private final ScriptEngineFactory factory;

        Engine(ScriptEngineFactory factory) {
            this.factory = factory;
        }

        @Override
        public Object eval(String script, ScriptContext context) throws ScriptException {
            if (!ACTIVE.get()) {
                throw new ScriptException("Scripts non pris en charge par spec-writer");
            }
            return null;
        }

        @Override
        public Object eval(Reader reader, ScriptContext context) throws ScriptException {
            return eval("", context);
        }

        @Override
        public Bindings createBindings() {
            return new SimpleBindings();
        }

        @Override
        public ScriptEngineFactory getFactory() {
            return factory;
        }
    }
}
