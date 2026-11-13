package dev.specwriter.infrastructure.sources;

import dev.specwriter.domain.sources.ModuleDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Lecture d'un {@code pom.xml} (013, research.md R5) avec le DOM du JDK, protégé contre XXE : pas de
 * DOCTYPE, pas d'entité externe, pas de XInclude. Seules les coordonnées comptent : versions et
 * propriétés ne sont pas résolues. Un pom illisible ne fait pas échouer l'analyse.
 */
final class MavenPomReader {

    /** Aucune erreur de lecture écrite sur la console : elle pourrait citer le contenu du fichier. */
    private static final org.xml.sax.ErrorHandler SILENT = new org.xml.sax.ErrorHandler() {
        @Override
        public void warning(org.xml.sax.SAXParseException e) {
            // ignorée
        }

        @Override
        public void error(org.xml.sax.SAXParseException e) {
            // ignorée
        }

        @Override
        public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
            throw e;
        }
    };

    private MavenPomReader() {
    }

    static ModuleDescriptor read(InputStream in) {
        Document doc;
        try {
            doc = builder().parse(in);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return ModuleDescriptor.unreadable();
        }
        Element project = doc.getDocumentElement();
        if (project == null || !"project".equals(name(project))) {
            return ModuleDescriptor.unreadable();
        }
        Counter unresolved = new Counter();
        Element parentElement = child(project, "parent");
        String parentGroup = parentElement == null ? null : text(parentElement, "groupId");
        String parent = parentElement == null ? null
                : coordinates(parentGroup, text(parentElement, "artifactId"), null, unresolved);
        String artifactId = text(project, "artifactId");
        String coordinates = coordinates(text(project, "groupId"), artifactId, parentGroup, unresolved);

        Set<String> dependencies = new LinkedHashSet<>();
        collect(child(project, "dependencies"), dependencies, unresolved);
        Element profiles = child(project, "profiles");
        for (Element profile : children(profiles, "profile")) {
            collect(child(profile, "dependencies"), dependencies, unresolved);
        }
        Set<String> boms = new LinkedHashSet<>();
        for (Element dependency : children(child(child(project, "dependencyManagement"), "dependencies"), "dependency")) {
            if ("import".equals(text(dependency, "scope")) && "pom".equals(text(dependency, "type"))) {
                add(boms, coordinates(text(dependency, "groupId"), text(dependency, "artifactId"), null, unresolved));
            }
        }
        List<String> modules = new ArrayList<>();
        for (Element module : children(child(project, "modules"), "module")) {
            String path = module.getTextContent().strip().replace('\\', '/');
            while (path.startsWith("./")) {
                path = path.substring(2);
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (!path.isEmpty() && !path.contains("${")) {
                modules.add(path);
            }
        }
        String name = artifactId == null || artifactId.contains("${") ? null : artifactId;
        return new ModuleDescriptor(name, coordinates, parent, dependencies, boms, modules, unresolved.value);
    }

    private static DocumentBuilder builder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(SILENT);
        return builder;
    }

    private static void collect(Element dependencies, Set<String> into, Counter unresolved) {
        for (Element dependency : children(dependencies, "dependency")) {
            add(into, coordinates(text(dependency, "groupId"), text(dependency, "artifactId"), null, unresolved));
        }
    }

    private static void add(Set<String> into, String coordinates) {
        if (coordinates != null) {
            into.add(coordinates);
        }
    }

    /** {@code groupId:artifactId}, avec le {@code groupId} du parent à défaut ; null si non résolu. */
    private static String coordinates(String groupId, String artifactId, String inheritedGroup, Counter unresolved) {
        String group = groupId != null ? groupId : inheritedGroup;
        if (group == null || artifactId == null) {
            return null;
        }
        if (group.contains("${") || artifactId.contains("${")) {
            unresolved.value++;
            return null;
        }
        return group + ":" + artifactId;
    }

    private static String text(Element parent, String child) {
        Element element = child(parent, child);
        if (element == null) {
            return null;
        }
        String value = element.getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    private static Element child(Element parent, String child) {
        List<Element> found = children(parent, child);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static List<Element> children(Element parent, String child) {
        List<Element> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && child.equals(name(e))) {
                out.add(e);
            }
        }
        return out;
    }

    private static String name(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private static final class Counter {
        int value;
    }
}
