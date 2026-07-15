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

export interface RagEvaluationCaseDto {
  caseKey: string;
  question: string;
  expectedRelevantChunkIds: string[];
  expectedRelevantDocumentIds: number[];
  expectedRefusal: boolean;
  expectedAnswerKeywords: string[];
}

export interface RagEvaluationDatasetDto {
  id: number;
  name: string;
  description: string;
  latestVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface RagEvaluationDatasetVersionDto {
  id: number;
  datasetId: number;
  version: number;
  changeNote: string;
  cases: RagEvaluationCaseDto[];
  createdAt: string;
}

export interface RagEvaluationMetricsDto {
  caseCount: number;
  recallAtK: number;
  meanReciprocalRank: number;
  citationCoverage: number;
  refusalAccuracy: number;
  answerKeywordCoverage: number;
  averageLatencyMillis: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  tokenUsageEstimated: boolean;
  estimatedCostUsd: number;
}

export interface RagEvaluationCaseResultDto {
  caseKey: string;
  question: string;
  retrievedSources: Array<{
    chunkId: string;
    documentId: number;
    rank: number;
    score: number;
  }>;
  relevantRetrievedCount: number;
  relevantExpectedCount: number;
  firstRelevantRank?: number;
  recallAtK: number;
  reciprocalRank: number;
  citationCovered: boolean;
  expectedRefusal: boolean;
  actualRefusal: boolean;
  refusalCorrect: boolean;
  answerKeywordCoverage: number;
  elapsedMillis: number;
  promptTokens: number;
  completionTokens: number;
  tokenUsageEstimated: boolean;
  estimatedCostUsd: number;
}

export type RagEvaluationJobStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface RagEvaluationJobDto {
  id: number;
  datasetId: number;
  datasetVersion: number;
  status: RagEvaluationJobStatus;
  retrievalStrategy: string;
  topK: number;
  promptVersion: string;
  modelName: string;
  baselineJobId?: number;
  metrics?: RagEvaluationMetricsDto;
  caseResults: RagEvaluationCaseResultDto[];
  failureReason?: string;
  createdAt: string;
  startedAt: string;
  completedAt?: string;
}

export interface RagEvaluationComparisonDto {
  currentJobId: number;
  baselineJobId?: number;
  comparable: boolean;
  message: string;
  delta?: {
    recallAtK: number;
    meanReciprocalRank: number;
    citationCoverage: number;
    refusalAccuracy: number;
    answerKeywordCoverage: number;
    averageLatencyMillis: number;
    totalTokens: number;
    estimatedCostUsd: number;
  };
}

export interface RagEvaluationDashboardDto {
  datasetCount: number;
  totalJobCount: number;
  successfulJobCount: number;
  latestSuccessfulJob?: RagEvaluationJobDto;
  latestComparison?: RagEvaluationComparisonDto;
  recentJobs: RagEvaluationJobDto[];
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
  sourceDocumentId?: number;
  topic?: string;
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
  tasks: Array<{
    id: number;
    type: 'DUE_REVIEW' | 'WEAK_POINT_REVIEW' | 'TOPIC_REVIEW' | 'DOCUMENT_REVIEW'
      | 'MASTERY_REINFORCEMENT' | 'CONVERSATION_REVIEW';
    priority: 'HIGH' | 'MEDIUM' | 'LOW';
    status: 'PENDING' | 'COMPLETED' | 'SKIPPED';
    scheduledDate: string;
    topic?: string;
    sourceDocumentId?: number;
    targetCardCount: number;
    completedCardCount: number;
    completed: boolean;
    reason: string;
    flashcardIds: number[];
    feedbackScore?: number;
    feedbackComment?: string;
    completedAt?: string;
    skippedAt?: string;
    version: number;
    updatedAt: string;
  }>;
  updatedAt: string;
}

export interface LearningTopicProfileDto {
  topic: string;
  cardCount: number;
  reviewCount: number;
  successRate: number;
  lapseRate: number;
  masteryScore: number;
  level: 'WEAK' | 'AT_RISK' | 'STABLE' | 'STRONG';
  lastReviewedAt?: string;
  updatedAt: string;
}

export interface ConversationLearningSummaryDto {
  id: number;
  conversationId: number;
  summary: string;
  topics: string[];
  weakTopics: string[];
  messageCount: number;
  version: number;
  updatedAt: string;
}

export interface StudyMaintenanceStatusDto {
  running: boolean;
  lastStartedAt?: string;
  lastCompletedAt?: string;
  lastDurationMillis: number;
  processedScopes: number;
  optimizationJobs: number;
  compensatedTasks: number;
  rescheduledTasks: number;
  failureCount: number;
  lastError?: string;
}

export interface StudyReviewSessionDto {
  id: number;
  workspaceId: number;
  status: 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ABANDONED';
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
  pausedAt?: string;
  completedAt?: string;
  abandonedAt?: string;
  updatedAt: string;
}

export interface StudyTrendDto {
  from: string;
  to: string;
  totalReviews: number;
  uniqueFlashcards: number;
  activeDays: number;
  accuracy: number;
  daily: Array<{
    date: string;
    reviewCount: number;
    uniqueFlashcards: number;
    correctCount: number;
    lapseCount: number;
    accuracy: number;
  }>;
  weekly: Array<{
    weekStart: string;
    weekEnd: string;
    reviewCount: number;
    uniqueFlashcards: number;
    activeDays: number;
    correctCount: number;
    lapseCount: number;
    accuracy: number;
  }>;
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
