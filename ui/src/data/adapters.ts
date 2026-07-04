import type { KnowledgeDocumentDto, RagTraceDto } from '../api/contracts';
import type { KnowledgeItem } from '../types';

export function toKnowledgeDocumentDto(item: KnowledgeItem): KnowledgeDocumentDto {
  return {
    id: item.id,
    title: item.title,
    sourceType: item.sourceType,
    workspaceId: item.workspace,
    workspaceName: item.workspace,
    tags: item.tags,
    ingestionStatus: item.status,
    chunkCount: item.chunks,
    updatedAt: item.updatedAt,
  };
}

export const ragTraces: RagTraceDto[] = [
  {
    id: 'trace-001',
    question: '线程池参数怎么理解？',
    strategy: '向量检索',
    topK: 5,
    latencyMs: 2100,
    result: '已引用来源',
  },
  {
    id: 'trace-002',
    question: 'Spring AI 如何做 Tool Calling？',
    strategy: '混合检索',
    topK: 8,
    latencyMs: 2800,
    result: '等待评估',
  },
];
