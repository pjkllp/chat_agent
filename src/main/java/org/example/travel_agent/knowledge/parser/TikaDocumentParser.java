package org.example.travel_agent.knowledge.parser;

import cn.hutool.core.util.StrUtil;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.example.travel_agent.Exceptions.ClientException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public String parse(String fileName, InputStream inputStream) throws IOException, TikaException, ClientException {
        if (inputStream == null) {
            throw new ClientException("该知识库不存在");
        }
        Metadata metadata = new Metadata();
        if (StrUtil.isNotBlank(fileName)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        return tika.parseToString(inputStream, metadata);
    }
}
