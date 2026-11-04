package dev.specwriter.infrastructure.embedding;

import dev.specwriter.domain.port.EmbeddingPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jboss.logging.Logger;

/**
 * Téléchargement d'un modèle d'embedding dans {@code ${data}/models/<nom>/} (research.md R19) :
 * fichiers à une révision épinglée, SHA-256 vérifiés, reprise d'un téléchargement incomplet par
 * {@code Range}. Un fichier altéré est supprimé. Les redirections (Hugging Face renvoie vers son CDN)
 * sont suivies ici, quel que soit le client HTTP fourni.
 */
public final class ModelDownloader {

    private static final Logger LOG = Logger.getLogger(ModelDownloader.class);
    private static final String PART = ".part";
    private static final String VERIFIED = ".sha256";
    private static final int BUFFER = 64 * 1024;
    private static final int MAX_REDIRECTS = 5;

    /** Fichier du modèle : chemin dans le dépôt, empreinte et taille attendues. */
    public record ModelFile(String path, String sha256, long size) {

        public String fileName() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    /** Modèle d'un dépôt Hugging Face à une révision donnée. */
    public record Spec(String name, URI base, String repository, String revision, List<ModelFile> files) {

        public Spec {
            Objects.requireNonNull(name);
            Objects.requireNonNull(base);
            files = List.copyOf(files);
        }

        public String id() {
            return repository + "@" + revision;
        }

        public Spec withBase(URI newBase) {
            return new Spec(name, newBase, repository, revision, files);
        }

        URI uri(ModelFile file) {
            String root = base.toString().endsWith("/") ? base.toString() : base + "/";
            return URI.create(root + repository + "/resolve/" + revision + "/" + file.path());
        }

        long totalSize() {
            return files.stream().mapToLong(ModelFile::size).sum();
        }
    }

    /** Échec du téléchargement, sans contenu ni adresse de proxy. */
    public static final class ModelException extends Exception {
        private static final long serialVersionUID = 1L;

        public enum Reason {
            UNREACHABLE,
            HTTP_ERROR,
            CORRUPTED,
            CANCELLED,
            IO
        }

        private final Reason reason;
        private final Integer status;

        ModelException(Reason reason, String message, Integer status) {
            super(message);
            this.reason = reason;
            this.status = status;
        }

        public Reason reason() {
            return reason;
        }

        public Integer status() {
            return status;
        }
    }

    private final HttpClient http;
    private final Path models;

    public ModelDownloader(HttpClient http, Path models) {
        this.http = http;
        this.models = models;
    }

    public Path folder(Spec spec) {
        return models.resolve(spec.name());
    }

    /** Vrai si chaque fichier est présent, à la bonne taille, et déjà vérifié pour cette empreinte. */
    public boolean present(Spec spec) {
        return spec.files().stream().allMatch(f -> verified(folder(spec), f));
    }

    /** Télécharge ce qui manque ; renvoie le dossier du modèle. */
    public Path ensure(Spec spec, EmbeddingPort.Progress progress) throws ModelException {
        Path folder = folder(spec);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new ModelException(ModelException.Reason.IO, "Dossier du modèle impossible à créer", null);
        }
        long[] done = {0};
        long total = spec.totalSize();
        for (ModelFile file : spec.files()) {
            if (!verified(folder, file)) {
                download(spec, folder, file, progress, done, total);
            }
            done[0] = Math.max(done[0], doneUpTo(spec, file));
            progress.advanced(done[0], total);
        }
        return folder;
    }

    private static long doneUpTo(Spec spec, ModelFile last) {
        long sum = 0;
        for (ModelFile f : spec.files()) {
            sum += f.size();
            if (f.equals(last)) {
                break;
            }
        }
        return sum;
    }

    private void download(Spec spec, Path folder, ModelFile file, EmbeddingPort.Progress progress, long[] done,
            long total) throws ModelException {
        Path target = folder.resolve(file.fileName());
        Path part = folder.resolve(file.fileName() + PART);
        long offset = sizeOf(part);
        if (offset >= file.size()) {
            offset = 0;
        }
        HttpResponse<InputStream> response = fetch(spec.uri(file), offset);
        int status = response.statusCode();
        boolean resumed = status == 206 && offset > 0;
        if (status != 200 && !resumed) {
            close(response);
            throw new ModelException(ModelException.Reason.HTTP_ERROR, "Modèle : HTTP " + status, status);
        }
        long start = resumed ? offset : 0;
        long base = done[0];
        try (InputStream in = response.body();
                OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        resumed ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[BUFFER];
            long written = start;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (progress.cancelled() || Thread.currentThread().isInterrupted()) {
                    throw new ModelException(ModelException.Reason.CANCELLED, "Téléchargement interrompu", null);
                }
                out.write(buffer, 0, read);
                written += read;
                if (written > file.size()) {
                    break;
                }
                progress.advanced(base + written, total);
            }
        } catch (IOException e) {
            throw new ModelException(ModelException.Reason.UNREACHABLE,
                    "Téléchargement du modèle coupé (" + e.getClass().getSimpleName() + ")", null);
        }
        String actual = sha256(part);
        if (!file.sha256().equalsIgnoreCase(actual)) {
            deleteQuietly(part);
            deleteQuietly(target);
            LOG.warnf("Modèle d'embedding : %s altéré (empreinte inattendue), supprimé", file.fileName());
            throw new ModelException(ModelException.Reason.CORRUPTED,
                    "Fichier du modèle altéré : " + file.fileName(), null);
        }
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(folder.resolve(file.fileName() + VERIFIED), file.sha256());
        } catch (IOException e) {
            throw new ModelException(ModelException.Reason.IO, "Fichier du modèle impossible à écrire", null);
        }
    }

    /** GET du fichier, en suivant au plus {@value #MAX_REDIRECTS} redirections ; le {@code Range} est gardé. */
    private HttpResponse<InputStream> fetch(URI uri, long offset) throws ModelException {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(current).timeout(Duration.ofMinutes(30)).GET();
            if (offset > 0) {
                request.header("Range", "bytes=" + offset + "-");
            }
            HttpResponse<InputStream> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException e) {
                throw new ModelException(ModelException.Reason.UNREACHABLE,
                        "Modèle injoignable (" + e.getClass().getSimpleName() + ")", null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelException(ModelException.Reason.CANCELLED, "Téléchargement interrompu", null);
            }
            int status = response.statusCode();
            String location = response.headers().firstValue("Location").orElse(null);
            if (!redirect(status) || location == null) {
                return response;
            }
            close(response);
            try {
                current = current.resolve(location);
            } catch (IllegalArgumentException e) {
                throw new ModelException(ModelException.Reason.HTTP_ERROR, "Modèle : redirection invalide", status);
            }
        }
        throw new ModelException(ModelException.Reason.HTTP_ERROR, "Modèle : trop de redirections", null);
    }

    private static boolean redirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean verified(Path folder, ModelFile file) {
        Path target = folder.resolve(file.fileName());
        Path marker = folder.resolve(file.fileName() + VERIFIED);
        try {
            return Files.isRegularFile(target) && Files.size(target) == file.size() && Files.isRegularFile(marker)
                    && Files.readString(marker).strip().equalsIgnoreCase(file.sha256());
        } catch (IOException e) {
            return false;
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private static String sha256(Path file) throws ModelException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[BUFFER];
            while (in.read(buffer) >= 0) {
                // l'empreinte se calcule à la lecture
            }
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ModelException(ModelException.Reason.IO, "Fichier du modèle illisible", null);
        }
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException e) {
            LOG.debugf("Réponse abandonnée mal fermée (%s)", e.getClass().getSimpleName());
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.debugf("Suppression impossible (%s)", e.getClass().getSimpleName());
        }
    }
}
