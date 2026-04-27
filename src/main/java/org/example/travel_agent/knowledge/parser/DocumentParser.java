package org.example.travel_agent.knowledge.parser;

import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface DocumentParser {

    ParsedDocument parse(String fileName, InputStream inputStream) throws IOException, TikaException;

    record ParsedDocument(String content, Map<String, String> metadata) {
    }
}
