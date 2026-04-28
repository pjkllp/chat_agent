package org.example.travel_agent.knowledge.parser;

import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface DocumentParser {

    /**
     * @return 解析得到的纯文本
     */
    String parse(String fileName, InputStream inputStream) throws IOException, TikaException, ClientException;

}
