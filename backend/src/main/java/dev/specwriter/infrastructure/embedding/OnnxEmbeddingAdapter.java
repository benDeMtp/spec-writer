package dev.specwriter.infrastructure.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.infrastructure.embedding.ModelDownloader.ModelException;
import dev.specwriter.infrastructure.embedding.ModelDownloader.ModelFile;
import dev.specwriter.infrastructure.embedding.ModelDownloader.Spec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Embedding local par ONNX Runtime (research.md R19) : modèle par défaut téléchargé une fois, ou
 * dossier local déclaré dans Configuration. Sans modèle utilisable, la recherche se fait par les
 * termes seuls ({@link State#TERMS_ONLY}), sans exception.
 */
public class OnnxEmbeddingAdapter implements EmbeddingPort {

    private static final Logger LOG = Logger.getLogger(OnnxEmbeddingAdapter.class);

    /** Xenova/multilingual-e5-small, révision et empreintes relevées sur Hugging Face le 2026-11-04. */
    public static final Spec DEFAULT_SPEC = new Spec("multilingual-e5-small", URI.create("https://huggingface.co"),
            "Xenova/multilingual-e5-small", "761b726dd34fb83930e26aab4e9ac3899aa1fa78",
            List.of(new ModelFile("onnx/model_quantized.onnx",
                            "f80102d3f2a1229f387d3c81909990d8945513e347b0eab049f7de3c6f98c193", 118_308_185L),
                    new ModelFile("tokenizer.json",
                            "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39", 17_082_730L)));

    /** Réglages : dossier local (nul pour le modèle par défaut), pooling et préfixes. */
    public record Settings(String folder, PoolingMode pooling, String queryPrefix, String passagePrefix) {

        /** Préfixes de e5 (research.md R19). */
        public static final Settings DEFAULT = new Settings(null, PoolingMode.MEAN, "query: ", "passage: ");

        public Settings {
            pooling = pooling == null ? PoolingMode.MEAN : pooling;
            queryPrefix = queryPrefix == null ? "" : queryPrefix;
            passagePrefix = passagePrefix == null ? "" : passagePrefix;
        }
    }

    private record Loaded(OnnxEmbeddingModel model, String id, Settings settings) {
    }

    private final ModelDownloader downloader;
    private final Spec spec;
    private final Supplier<Settings> settings;
    private final Path djlCache;

    private volatile State state = State.MISSING;
    private volatile Loaded loaded;
    private volatile String modelId;
    private volatile String problem;

    public OnnxEmbeddingAdapter(ModelDownloader downloader, Spec spec, Supplier<Settings> settings, Path djlCache) {
        this.downloader = downloader;
        this.spec = spec;
        this.settings = settings;
        this.djlCache = djlCache;
        this.modelId = spec.id();
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    /** Motif du dernier échec, sans contenu, pour l'indexation et le journal. */
    @Override
    public String problem() {
        return problem;
    }

    @Override
    public synchronized State prepare(Progress progress) {
        Settings current = settings.get();
        if (state == State.READY && loaded != null && current.equals(loaded.settings())) {
            return state;
        }
        loaded = null;
        problem = null;
        offline();
        try {
            Path onnx;
            Path tokenizer;
            String id;
            if (current.folder() != null && !current.folder().isBlank()) {
                Path folder = Path.of(current.folder());
                onnx = find(folder, "model.onnx", ".onnx");
                tokenizer = find(folder, "tokenizer.json", "tokenizer.json");
                id = "local:" + folder.getFileName() + "@" + fingerprint(onnx, current);
            } else {
                id = spec.id() + (current.equals(Settings.DEFAULT) ? "" : "@" + fingerprint(null, current));
                modelId = id;
                if (!downloader.present(spec)) {
                    state = State.DOWNLOADING;
                }
                Path folder = downloader.ensure(spec, progress);
                onnx = folder.resolve(spec.files().get(0).fileName());
                tokenizer = folder.resolve(spec.files().get(1).fileName());
            }
            modelId = id;
            OnnxEmbeddingModel model = new OnnxEmbeddingModel(onnx.toString(), tokenizer.toString(), current.pooling());
            loaded = new Loaded(model, id, current);
            state = State.READY;
            LOG.infof("Modèle d'embedding prêt : %s", id);
        } catch (ModelException e) {
            fail(e.reason() + " : " + e.getMessage());
        } catch (IOException | RuntimeException | LinkageError e) {
            // ONNX Runtime ou le tokenizer refusent un fichier illisible par des exceptions non vérifiées ;
            // une bibliothèque native introuvable laisse la recherche par les termes (FR-043)
            fail(e.getClass().getSimpleName());
        }
        return state;
    }

    @Override
    public float[] query(String text) {
        Loaded current = loaded;
        return current == null ? null : current.model().embed(current.settings().queryPrefix() + text).content().vector();
    }

    @Override
    public List<float[]> passages(List<String> texts) {
        Loaded current = loaded;
        if (current == null || texts.isEmpty()) {
            return List.of();
        }
        List<TextSegment> segments = texts.stream().map(t -> TextSegment.from(current.settings().passagePrefix() + t))
                .toList();
        return current.model().embedAll(segments).content().stream().map(Embedding::vector).toList();
    }

    private void fail(String reason) {
        problem = reason;
        state = State.TERMS_ONLY;
        LOG.warnf("Modèle d'embedding indisponible (%s) : recherche par les termes seuls", reason);
    }

    /** Natifs pris dans le jar, extraits dans le dossier de données, jamais téléchargés. */
    private void offline() {
        System.setProperty("ai.djl.offline", "true");
        if (System.getProperty("DJL_CACHE_DIR") == null && System.getenv("DJL_CACHE_DIR") == null) {
            System.setProperty("DJL_CACHE_DIR", djlCache.toString());
        }
    }

    private static Path find(Path folder, String preferred, String suffix) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IOException("Dossier du modèle introuvable");
        }
        Path exact = folder.resolve(preferred);
        if (Files.isRegularFile(exact)) {
            return exact;
        }
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> candidates = files.filter(f -> Optional.ofNullable(f.getFileName()).map(Path::toString)
                    .filter(n -> n.endsWith(suffix)).isPresent()).toList();
            if (candidates.size() != 1) {
                throw new IOException("Fichier " + preferred + " introuvable");
            }
            return candidates.get(0);
        }
    }

    /** Empreinte courte du fichier et des réglages : un changement impose la revectorisation. */
    private static String fingerprint(Path onnx, Settings settings) throws IOException {
        String descriptor = (onnx == null ? "" : onnx.toAbsolutePath() + "|" + Files.size(onnx) + "|"
                + Files.getLastModifiedTime(onnx).toMillis()) + "|" + settings.pooling() + "|"
                + settings.queryPrefix() + "|" + settings.passagePrefix();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(descriptor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
