package dev.specwriter.infrastructure.architecture;

import com.structurizr.Workspace;
import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.DeploymentNode;
import com.structurizr.model.Element;
import com.structurizr.model.Person;
import com.structurizr.model.Relationship;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.model.StaticStructureElement;
import com.structurizr.model.StaticStructureElementInstance;
import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.port.ArchitectureReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Lecture d'un workspace Structurizr (FR-038, research.md R21) par l'analyseur officiel, en mode
 * restreint : seuls le système de fichiers et {@code !include} sont permis ; ni script, ni plugin,
 * ni HTTP, ni variable d'environnement. Le contenu des fichiers n'est jamais journalisé.
 */
public class StructurizrReader implements ArchitectureReader {

    private static final Logger LOG = Logger.getLogger(StructurizrReader.class);
    private static final String OWNERSHIP = "ownership";
    /** Dossier d'infrastructure partagée entre workspaces (research.md R21). */
    private static final String SHARED_FOLDER = "infra";
    private static final int MAX_FILES = 500;

    private static final Pattern EXTENDS = Pattern.compile("(?m)^\\s*workspace\\s+(?:\"[^\"]*\"\\s+)*extends\\s+\"?([^\"\\s{]+)\"?");
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*!include\\s+\"?([^\"\\s]+)\"?");
    private static final Pattern NODE = Pattern.compile("deploymentNode\\s+\"([^\"]+)\"");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern ERROR_AT = Pattern.compile("^(.*?) at line (\\d+) of (.+?): .*$", Pattern.DOTALL);

    @Override
    public Result read(String workspace, String ownership) {
        Path file = Path.of(workspace).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            return failure(file.toString(), null, "Fichier workspace introuvable ou n'est pas un fichier");
        }
        if (!Files.isReadable(file)) {
            return failure(file.toString(), null, "Fichier workspace illisible (droits)");
        }
        Workspace parsed;
        try {
            parsed = parse(file.toFile());
        } catch (StructurizrDslParserException e) {
            return failure(e);
        } catch (RuntimeException e) {
            return failure(file.toString(), null, "Analyse impossible (" + e.getClass().getSimpleName() + ")");
        }
        Set<Path> files = files(file);
        Set<String> common = extended(file).map(StructurizrReader::canonicalNames).orElse(Set.of());
        Set<String> sharedNodes = sharedNodes(files);
        ArchitectureModel model = new ArchitectureModel(elements(parsed, ownership, common), relations(parsed),
                deployments(parsed, sharedNodes));
        List<String> names = files.stream().map(Path::toString).sorted().toList();
        return new Result(model, names, hash(files), null);
    }

    private static Workspace parse(File file) throws StructurizrDslParserException {
        StructurizrDslParser parser = new StructurizrDslParser();
        parser.setRestricted(true);
        parser.getFeatures().enable(Features.FILE_SYSTEM);
        parser.getFeatures().enable(Features.INCLUDE);
        parser.parse(file);
        return parser.getWorkspace();
    }

    // ---------- Éléments ----------

    private static List<ArchElement> elements(Workspace workspace, String ownership, Set<String> common) {
        List<ArchElement> elements = new ArrayList<>();
        for (Element e : workspace.getModel().getElements()) {
            Optional<ArchElement.Type> type = type(e);
            if (type.isEmpty()) {
                continue;
            }
            String name = canonical(e);
            String technology = technology(e);
            boolean transport = ArchitectureModel.isTransport(e.getTagsAsSet(), technology);
            elements.add(new ArchElement(name, type.get(), scope(e, name, ownership, common), transport,
                    blankToNull(e.getDescription()), technology));
        }
        elements.sort(Comparator.comparing(ArchElement::name));
        return elements;
    }

    /** {@code ownership} fait foi s'il est renseigné ; sinon un élément du modèle étendu est commun. */
    private static ArchScope scope(Element e, String name, String ownership, Set<String> common) {
        String owner = e.getProperties().get(OWNERSHIP);
        if (ownership != null && !ownership.isBlank() && owner != null && !owner.isBlank()) {
            return owner.strip().equalsIgnoreCase(ownership.strip()) ? ArchScope.PROJECT : ArchScope.COMMON;
        }
        return common.contains(name) ? ArchScope.COMMON : ArchScope.PROJECT;
    }

    private static Optional<ArchElement.Type> type(Element e) {
        if (e instanceof Person) {
            return Optional.of(ArchElement.Type.PERSON);
        }
        if (e instanceof SoftwareSystem) {
            return Optional.of(ArchElement.Type.SOFTWARE_SYSTEM);
        }
        if (e instanceof Container) {
            return Optional.of(ArchElement.Type.CONTAINER);
        }
        if (e instanceof Component) {
            return Optional.of(ArchElement.Type.COMPONENT);
        }
        return Optional.empty();
    }

    /** {@code Système/Conteneur/Composant} ; une barre oblique dans un nom est remplacée. */
    static String canonical(Element e) {
        Deque<String> parts = new ArrayDeque<>();
        for (Element current = e; current != null; current = current.getParent()) {
            parts.addFirst(current.getName().replace('/', '∕'));
        }
        return String.join("/", parts);
    }

    private static String technology(Element e) {
        if (e instanceof Container c) {
            return blankToNull(c.getTechnology());
        }
        if (e instanceof Component c) {
            return blankToNull(c.getTechnology());
        }
        return null;
    }

    private static Set<String> canonicalNames(Workspace workspace) {
        Set<String> names = new HashSet<>();
        workspace.getModel().getElements().stream().filter(e -> type(e).isPresent()).map(StructurizrReader::canonical)
                .forEach(names::add);
        return names;
    }

    /** Le modèle étendu, analysé seul ; vide s'il n'y en a pas ou s'il ne s'analyse pas. */
    private static Optional<Workspace> extended(Path workspace) {
        return read(workspace).flatMap(text -> {
            Matcher m = EXTENDS.matcher(text);
            if (!m.find()) {
                return Optional.empty();
            }
            Path base = workspace.resolveSibling(m.group(1)).normalize();
            try {
                return Optional.of(parse(base.toFile()));
            } catch (StructurizrDslParserException | RuntimeException e) {
                LOG.debugf("Modèle étendu non analysé seul (%s)", e.getClass().getSimpleName());
                return Optional.empty();
            }
        });
    }

    // ---------- Relations et déploiements ----------

    private static List<ArchRelation> relations(Workspace workspace) {
        List<ArchRelation> relations = new ArrayList<>();
        for (Relationship r : workspace.getModel().getRelationships()) {
            if (r.getLinkedRelationshipId() != null || type(r.getSource()).isEmpty()
                    || type(r.getDestination()).isEmpty()) {
                continue;
            }
            relations.add(new ArchRelation(canonical(r.getSource()), canonical(r.getDestination()),
                    blankToNull(r.getDescription()), blankToNull(r.getTechnology())));
        }
        return relations;
    }

    private static List<ArchDeployment> deployments(Workspace workspace, Set<String> sharedNodes) {
        List<ArchDeployment> deployments = new ArrayList<>();
        List<DeploymentNode> roots = workspace.getModel().getDeploymentNodes().stream()
                .filter(n -> n.getParent() == null).sorted(Comparator.comparing(DeploymentNode::getName)).toList();
        for (DeploymentNode root : roots) {
            walk(root, root, List.of(root.getName()), count(root.getInstances()), sharedNodes, deployments);
        }
        return deployments;
    }

    private static void walk(DeploymentNode node, DeploymentNode root, List<String> path, int instances,
            Set<String> sharedNodes, List<ArchDeployment> out) {
        boolean shared = path.stream().anyMatch(sharedNodes::contains);
        List<StaticStructureElementInstance> hosted = new ArrayList<>();
        hosted.addAll(node.getSoftwareSystemInstances());
        hosted.addAll(node.getContainerInstances());
        hosted.sort(Comparator.comparing(i -> canonical(i.getElement())));
        for (StaticStructureElementInstance instance : hosted) {
            StaticStructureElement element = instance.getElement();
            out.add(new ArchDeployment(canonical(element), node.getEnvironment(), root.getName(), String.join("/", path),
                    instances, shared));
        }
        node.getChildren().stream().sorted(Comparator.comparing(DeploymentNode::getName)).forEach(child -> {
            List<String> childPath = new ArrayList<>(path);
            childPath.add(child.getName());
            walk(child, root, childPath, instances * count(child.getInstances()), sharedNodes, out);
        });
    }

    /** {@code "2"} ou {@code "1..N"} : le nombre minimal annoncé, au moins 1. */
    private static int count(String instances) {
        if (instances == null) {
            return 1;
        }
        Matcher m = NUMBER.matcher(instances);
        return m.find() ? Math.max(1, Integer.parseInt(m.group())) : 1;
    }

    /** Nœuds déclarés dans un fichier inclus depuis un dossier {@code infra/}. */
    private static Set<String> sharedNodes(Set<Path> files) {
        Set<String> nodes = new HashSet<>();
        for (Path f : files) {
            boolean shared = false;
            for (Path segment : f) {
                shared |= SHARED_FOLDER.equals(segment.toString().toLowerCase(Locale.ROOT));
            }
            if (shared) {
                read(f).ifPresent(text -> {
                    Matcher m = NODE.matcher(text);
                    while (m.find()) {
                        nodes.add(m.group(1));
                    }
                });
            }
        }
        return nodes;
    }

    // ---------- Fichiers ----------

    /** Le workspace, le modèle qu'il étend et les fichiers inclus, récursivement. */
    private static Set<Path> files(Path workspace) {
        Set<Path> seen = new LinkedHashSet<>();
        Deque<Path> queue = new ArrayDeque<>(List.of(workspace));
        while (!queue.isEmpty() && seen.size() < MAX_FILES) {
            Path f = queue.poll();
            if (!seen.add(f)) {
                continue;
            }
            read(f).ifPresent(text -> queue.addAll(referenced(f, text)));
        }
        return seen;
    }

    private static List<Path> referenced(Path file, String text) {
        List<Path> referenced = new ArrayList<>();
        for (Pattern p : List.of(EXTENDS, INCLUDE)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                Path next = file.resolveSibling(m.group(1)).normalize();
                if (Files.isRegularFile(next)) {
                    referenced.add(next);
                } else if (Files.isDirectory(next)) {
                    try (Stream<Path> children = Files.list(next)) {
                        children.filter(c -> c.toString().endsWith(".dsl")).sorted().forEach(referenced::add);
                    } catch (IOException e) {
                        LOG.debugf("Dossier inclus illisible (%s)", e.getClass().getSimpleName());
                    }
                }
            }
        }
        return referenced;
    }

    private static Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String hash(Set<Path> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path f : files.stream().sorted().toList()) {
                digest.update(f.toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(read(f).orElse("").getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    // ---------- Erreurs ----------

    private static Result failure(StructurizrDslParserException e) {
        String message = e.getMessage() == null ? "Erreur d'analyse" : e.getMessage();
        Matcher m = ERROR_AT.matcher(message);
        if (m.matches()) {
            return failure(m.group(3), Integer.valueOf(m.group(2)), m.group(1).strip());
        }
        return failure(null, e.getLineNumber() > 0 ? e.getLineNumber() : null, message);
    }

    private static Result failure(String file, Integer line, String message) {
        LOG.warnf("Architecture : workspace non lu (%s%s) : %s", file == null ? "?" : file,
                line == null ? "" : ", ligne " + line, message);
        return new Result(ArchitectureModel.EMPTY, List.of(), null, new Problem(file, line, message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
