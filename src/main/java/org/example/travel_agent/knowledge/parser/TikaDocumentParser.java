package org.example.travel_agent.knowledge.parser;

import cn.hutool.core.util.StrUtil;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public ParsedDocument parse(String fileName, InputStream inputStream) throws IOException, TikaException {
        if (inputStream == null) {
            return new ParsedDocument("", Map.of());
        }
        Metadata metadata = new Metadata();
        if (StrUtil.isNotBlank(fileName)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        String content = tika.parseToString(inputStream, metadata);
        Map<String, String> metadataMap = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            metadataMap.put(name, metadata.get(name));
        }
        return new ParsedDocument(
                content == null ? "" : content.trim(),
                metadataMap
        );
    }
}
