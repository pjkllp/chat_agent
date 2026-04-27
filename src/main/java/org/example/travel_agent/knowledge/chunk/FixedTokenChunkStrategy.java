package org.example.travel_agent.knowledge.chunk;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FixedTokenChunkStrategy implements ChunkStrategy {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9_]+");

    private final int chunkSize;
    private final int overlap;

    public FixedTokenChunkStrategy(
            @Value("${knowledge.chunk.size:300}") int chunkSize,
            @Value("${knowledge.chunk.overlap:40}") int overlap
    ) {
        this.chunkSize = Math.max(1, chunkSize);
        this.overlap = Math.max(0, Math.min(overlap, this.chunkSize - 1));
    }

    @Override
    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        //把文本的文字分成一个个token，每个tokenSpan就是一个token的起始位置
        List<TokenSpan> spans = collectTokenSpans(text);
        if (spans.isEmpty()) {
            return List.of(text.trim());
        }

        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSize - overlap);
        for (int startIdx = 0; startIdx < spans.size(); startIdx += step) {
            int endIdx = Math.min(startIdx + chunkSize, spans.size());
            int startOffset = spans.get(startIdx).start();
            int endOffset = spans.get(endIdx - 1).end();
            String chunk = text.substring(startOffset, endOffset).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (endIdx >= spans.size()) {
                break;
            }
        }
        return chunks;
    }

    private List<TokenSpan> collectTokenSpans(String text) {
        List<TokenSpan> spans = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            spans.add(new TokenSpan(matcher.start(), matcher.end()));
        }
        return spans;
    }

    private record TokenSpan(int start, int end) {
    }
}
