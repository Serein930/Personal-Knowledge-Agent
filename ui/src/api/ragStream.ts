import {
  ApiClientError,
  buildApiUrl,
  getApiAuthenticationHeaders,
  handleApiUnauthorized,
} from './client';
import type { CreatedToolConfirmationDto, RagCitationDto, ToolCallSummaryDto } from './contracts';

interface RagStreamHandlers {
  onMetadata?: (data: { conversationId: number; messageId: number }) => void;
  onDelta?: (data: { sequence: number; content: string }) => void;
  onCitation?: (data: { citation: RagCitationDto }) => void;
  onToolCall?: (data: { toolCall: ToolCallSummaryDto }) => void;
  onConfirmationRequired?: (data: { proposal: CreatedToolConfirmationDto }) => void;
  onComplete?: () => void;
}

/**
 * 使用 fetch 读取 POST SSE。浏览器原生 EventSource 只支持 GET，无法携带问答请求体。
 */
export async function streamRagChat(
  workspaceId: number,
  question: string,
  conversationId: number | undefined,
  handlers: RagStreamHandlers,
) {
  const response = await fetch(buildApiUrl(`/v1/workspaces/${workspaceId}/rag/chat/stream`), {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...getApiAuthenticationHeaders(),
    },
    body: JSON.stringify({ conversationId, question }),
  });
  if (!response.ok || !response.body) {
    handleApiUnauthorized(response.status);
    throw new ApiClientError(`流式问答连接失败：${response.status}`, response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n');
    const frames = buffer.split('\n\n');
    buffer = frames.pop() ?? '';
    frames.forEach((frame) => dispatchFrame(frame, handlers));
    if (done) {
      if (buffer.trim()) dispatchFrame(buffer, handlers);
      break;
    }
  }
}

function dispatchFrame(frame: string, handlers: RagStreamHandlers) {
  let eventName = '';
  const dataLines: string[] = [];
  frame.split('\n').forEach((line) => {
    if (line.startsWith('event:')) eventName = line.slice(6).trim();
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  });
  if (!eventName || dataLines.length === 0) return;
  const data = JSON.parse(dataLines.join('\n')) as never;
  if (eventName === 'metadata') handlers.onMetadata?.(data);
  if (eventName === 'delta') handlers.onDelta?.(data);
  if (eventName === 'citation') handlers.onCitation?.(data);
  if (eventName === 'tool_call') handlers.onToolCall?.(data);
  if (eventName === 'tool_confirmation_required') handlers.onConfirmationRequired?.(data);
  if (eventName === 'complete') handlers.onComplete?.();
  if (eventName === 'error') throw new Error((data as { message?: string }).message ?? '流式回答失败');
}
