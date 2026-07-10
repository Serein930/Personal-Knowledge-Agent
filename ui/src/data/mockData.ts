import type { ChatCitation, KnowledgeItem, StudyTask, ToolTrace } from '../types';

// 第一阶段只使用本地 模拟 数据。后续接入后端时，可用同名 数据传输对象 逐步替换这些结构。
export const knowledgeItems: KnowledgeItem[] = [
  {
    id: 'doc-001',
    title: 'Java 并发编程笔记',
    sourceType: 'Markdown',
    workspace: 'Java 后端学习',
    tags: ['Java', '并发', '线程池'],
    status: '已完成',
    chunks: 48,
    updatedAt: '2026-07-04',
  },
  {
    id: 'doc-002',
    title: 'Spring AI 官方文档摘录',
    sourceType: '网页文章',
    workspace: 'Agent 工程化',
    tags: ['Spring AI', 'RAG', 'Tool Calling'],
    status: '处理中',
    chunks: 21,
    updatedAt: '2026-07-04',
  },
  {
    id: 'doc-003',
    title: 'JVM 调优面试资料',
    sourceType: 'PDF',
    workspace: '面试准备',
    tags: ['JVM', 'GC', '面试'],
    status: '等待中',
    chunks: 0,
    updatedAt: '2026-07-03',
  },
];

export const chatCitations: ChatCitation[] = [
  {
    title: 'Java 并发编程笔记',
    excerpt: '线程池通过核心线程数、最大线程数、阻塞队列和拒绝策略共同控制任务执行。',
    score: 0.91,
  },
  {
    title: 'JVM 调优面试资料',
    excerpt: '高并发服务中，线程数量和队列长度需要结合 CPU、IO 等待和响应时间综合评估。',
    score: 0.84,
  },
];

export const toolTraces: ToolTrace[] = [
  {
    name: 'searchDocuments',
    status: '成功',
    detail: '在 Java 后端学习空间中检索到 5 个相关片段',
  },
  {
    name: 'readDocumentExcerpt',
    status: '成功',
    detail: '读取前 2 个高相关 chunk 用于生成回答',
  },
  {
    name: 'createFlashcards',
    status: '等待',
    detail: '等待用户确认后生成复习卡片',
  },
];

export const studyTasks: StudyTask[] = [
  {
    title: 'Java 并发基础复习',
    progress: 68,
    focus: '线程池、volatile、synchronized',
  },
  {
    title: 'Spring AI RAG 实践',
    progress: 34,
    focus: 'Embedding、Vector Store、引用溯源',
  },
  {
    title: '面试项目表达训练',
    progress: 52,
    focus: '项目难点、架构取舍、评估指标',
  },
];
