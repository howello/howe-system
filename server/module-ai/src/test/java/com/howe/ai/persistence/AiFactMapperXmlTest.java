package com.howe.ai.persistence;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AiFactMapperXmlTest {
    @Test
    void mapperXmlIsWellFormed() {
        assertDoesNotThrow(() -> {
            String xml = Files.readString(Path.of("src/main/resources/mapper/ai/AiFactMapper.xml"));
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
        });
    }
}
