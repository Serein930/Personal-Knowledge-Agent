export type PageKey =
  | 'dashboard'
  | 'knowledge'
  | 'ingestion'
  | 'chat'
  | 'study'
  | 'evaluation';

export type KnowledgeSourceType = 'PDF' | 'Markdown' | '网页文章' | '代码片段';

export type IngestionStatus = '已完成' | '处理中' | '等待中' | '失败';

export interface KnowledgeItem {
  id: string;
  title: string;
  sourceType: KnowledgeSourceType;
  workspace: string;
  tags: string[];
  status: IngestionStatus;
  chunks: number;
  updatedAt: string;
}

export interface ChatCitation {
  title: string;
  excerpt: string;
  score: number;
}

export interface ToolTrace {
  name: string;
  status: '成功' | '等待' | '跳过';
  detail: string;
}

export interface StudyTask {
  title: string;
  progress: number;
  focus: string;
}
