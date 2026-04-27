package org.example.travel_agent.knowledge.chunk;

import java.util.List;

public interface ChunkStrategy {

    List<String> split(String text);
}
