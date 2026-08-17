package com.howe.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class AiModuleBoundaryTest {
    private static final Path MODULE_DIR = Path.of(".").toAbsolutePath().normalize();

    @Test
    void moduleHasOnlyExpectedProductionDependencies() throws Exception {
        Document pom = parse(MODULE_DIR.resolve("pom.xml"));
        List<String> productionDependencies = elements(pom, "dependency").stream()
            .filter(dependency -> !"test".equals(childText(dependency, "scope")))
            .map(dependency -> childText(dependency, "artifactId"))
            .collect(Collectors.toList());

        // 契约下沉后 module-ai 允许依赖 module-common（最底层）与 module-ai-common（AI 域契约层）；
        // 禁止依赖 module-blog、module-system、module-framework 或其它业务模块。
        assertEquals(List.of("module-common", "module-ai-common"), productionDependencies);
    }

    @Test
    void serverRegistersAiModulesAndAdminAggregatesThem() throws Exception {
        Document serverPom = parse(MODULE_DIR.resolve("../pom.xml"));
        Document adminPom = parse(MODULE_DIR.resolve("../module-admin/pom.xml"));

        for (String artifact : List.of("module-ai", "module-ai-common")) {
            assertTrue(elements(serverPom, "module").stream()
                .map(Element::getTextContent)
                .map(String::trim)
                .anyMatch(artifact::equals), "server 未注册 " + artifact);
            assertTrue(elements(adminPom, "dependency").stream()
                .map(dependency -> childText(dependency, "artifactId"))
                .anyMatch(artifact::equals), "admin 未汇聚 " + artifact);
        }
    }

    @Test
    void componentScanFailsFastWhenAiPersistenceIsMissing() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.scan("com.howe.ai");
            assertThrows(org.springframework.beans.factory.UnsatisfiedDependencyException.class, context::refresh);
        }
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(Files.newInputStream(path));
    }

    private static List<Element> elements(Document document, String localName) {
        return java.util.stream.IntStream.range(0, document.getElementsByTagNameNS("*", localName).getLength())
            .mapToObj(index -> (Element) document.getElementsByTagNameNS("*", localName).item(index))
            .toList();
    }

    private static String childText(Element parent, String localName) {
        return elements(parent.getOwnerDocument(), localName).stream()
            .filter(element -> element.getParentNode() == parent)
            .findFirst()
            .map(Element::getTextContent)
            .map(String::trim)
            .orElse("");
    }
}
