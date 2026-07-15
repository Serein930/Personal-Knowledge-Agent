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

export type BackendIngestionStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED';
export type BackendDocumentSourceType = 'PDF' | 'MARKDOWN' | 'WEB_PAGE' | 'WORD' | 'TEXT' | 'CODE';

export interface BackendDocumentDto {
  id: number;
  title: string;
  sourceType: BackendDocumentSourceType;
  workspaceId: number;
  workspaceName: string;
  tags: string[];
  ingestionStatus: BackendIngestionStatus;
  chunkCount: number;
  updatedAt: string;
}

export interface DocumentCreatedDto {
  documentId: number;
  taskId: number;
  status: BackendIngestionStatus;
}

export interface BackendIngestionTaskDto {
  id: number;
  documentId: number;
  taskType: 'FILE_UPLOAD' | 'WEB_PAGE_CAPTURE';
  status: BackendIngestionStatus;
  progress: number;
  source: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeNoteDto {
  id: number;
  workspaceId: number;
  sourceConversationId?: number;
  requestId: string;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface StudyFlashcardDto {
  id: number;
  workspaceId: number;
  sourceConversationId?: number;
  requestId: string;
  question: string;
  answer: string;
  explanation: string;
  status: 'NEW' | 'LEARNING' | 'REVIEW' | 'SUSPENDED';
  repetitionCount: number;
  intervalDays: number;
  easeFactor: number;
  lapseCount: number;
  dueAt: string;
  lastReviewedAt?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface StudyReviewStatisticsDto {
  dueCount: number;
  completedToday: number;
  accuracyToday: number;
  currentStreakDays: number;
  totalReviews: number;
  lapseRate: number;
  scoreDistribution: Array<{ score: number; count: number }>;
  maturity: {
    newCount: number;
    learningCount: number;
    youngCount: number;
    matureCount: number;
    suspendedCount: number;
  };
  generatedAt: string;
}

export interface DailyStudyPlanDto {
  id: number;
  workspaceId: number;
  planDate: string;
  dailyReviewTarget: number;
  dueCardSnapshot: number;
  completedReviews: number;
  remainingReviews: number;
  progress: number;
  completed: boolean;
  updatedAt: string;
}

export interface StudyReviewSessionDto {
  id: number;
  workspaceId: number;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED';
  totalCards: number;
  reviewedCards: number;
  correctCards: number;
  progress: number;
  queue: Array<{
    id: number;
    position: number;
    status: 'PENDING' | 'REVIEWED';
    score?: number;
    reviewedAt?: string;
    flashcard: StudyFlashcardDto;
  }>;
  startedAt: string;
  completedAt?: string;
}

export interface SubmittedSessionReviewDto {
  session: StudyReviewSessionDto;
  review: {
    flashcard: StudyFlashcardDto;
    reused: boolean;
  };
}

export type ToolConfirmationStatus =
  | 'PENDING_CONFIRMATION'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'FAILED';

export interface ToolConfirmationDto {
  id: number;
  workspaceId: number;
  conversationId?: number;
  messageId?: number;
  requestId: string;
  toolName: string;
  argumentSummary: string;
  status: ToolConfirmationStatus;
  execution?: { result?: unknown; reused: boolean };
  failureReason?: string;
  expiresAt: string;
}

export interface CreatedToolConfirmationDto {
  confirmation: ToolConfirmationDto;
  confirmationToken: string;
}

export interface DecidedToolConfirmationDto {
  confirmation: ToolConfirmationDto;
  reused: boolean;
}

export interface RagCitationDto {
  index: number;
  documentId: number;
  documentTitle: string;
  chunkId: string;
  excerpt: string;
  score: number;
}

export interface ToolCallSummaryDto {
  id: number;
  toolName: string;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';
  responseSummary?: string;
  errorMessage?: string;
}
