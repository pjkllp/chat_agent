import { getToken } from "../services/api";

export function useSSE() {
  let controller = null;

  function connect(url, body, handlers = {}) {
    const { onWorkflow, onAnswer, onError, onComplete } = handlers;
    controller = new AbortController();
    const token = getToken();

    fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    }).then(async (response) => {
      if (!response.ok) {
        onError?.(new Error(`SSE 请求失败: ${response.status}`));
        return;
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split("\n\n");
        buffer = blocks.pop() || "";

        for (const block of blocks) {
          if (!block.trim()) continue;
          parseSSEBlock(block, onWorkflow, onAnswer);
        }
      }
      onComplete?.();
    }).catch((err) => {
      if (err.name === "AbortError") return;
      onError?.(err);
    });

    return controller;
  }

  function abort() {
    controller?.abort();
    controller = null;
  }

  return { connect, abort };
}

function parseSSEBlock(block, onWorkflow, onAnswer) {
  const lines = block.split(/\r?\n/);
  let event = "message";
  const dataParts = [];

  for (const line of lines) {
    if (!line || line.startsWith(":")) continue;
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
      continue;
    }
    if (line.startsWith("data:")) {
      dataParts.push(line.slice(5).trimStart());
    }
  }

  if (!dataParts.length) return;
  const data = dataParts.join("\n");

  if (event === "workflow") {
    try {
      const parsed = JSON.parse(data);
      onWorkflow?.(parsed);
    } catch { /* skip unparseable */ }
  } else if (event === "answer") {
    onAnswer?.(data);
  }
}
