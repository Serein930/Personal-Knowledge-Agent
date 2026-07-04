import type { IngestionStatus, KnowledgeSourceType } from '../types';

export interface PageResult<T> {
  records: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface WorkspaceDto {
  id: string;
  name: string;
  description: string;
}

export interface KnowledgeDocumentDto {
  id: string;
  title: string;
  sourceType: KnowledgeSourceType;
  workspaceId: string;
  workspaceName: string;
  tags: string[];
  ingestionStatus: IngestionStatus;
  chunkCount: number;
  updatedAt: string;
}

export interface IngestionTaskDto {
  id: string;
  title: string;
  source: string;
  status: IngestionStatus;
  progress: number;
  createdAt: string;
}

export interface RagTraceDto {
  id: string;
  question: string;
  strategy: string;
  topK: number;
  latencyMs: number;
  result: string;
}
