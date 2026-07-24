import { Button, Empty, Progress, Tag } from 'antd';
import {
  ArrowUpRight,
  BrainCircuit,
  Clock3,
  FileText,
  GraduationCap,
  LibraryBig,
  MessageSquareText,
  RefreshCw,
  Sparkles,
  Target,
  UploadCloud,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentSourceType,
  BackendIngestionStatus,
  DashboardOverviewDto,
  DailyStudyPlanDto,
} from '../api/contracts';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { useAppSession } from '../contexts/AppSessionContext';

const sourceLabels: Record<BackendDocumentSourceType, string> = {
  PDF: 'PDF', MARKDOWN: 'Markdown', WEB_PAGE: '网页文章', WORD: 'Word', TEXT: '文本', CODE: '代码',
};

const statusViews: Record<BackendIngestionStatus, { label: string; color: string }> = {
  PENDING: { label: '等待中', color: 'default' },
  RUNNING: { label: '处理中', color: 'processing' },
  SUCCEEDED: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  CANCELED: { label: '已取消', color: 'default' },
};

const studyTaskLabels: Record<DailyStudyPlanDto['tasks'][number]['type'], string> = {
  DUE_REVIEW: '到期复习',
  WEAK_POINT_REVIEW: '薄弱知识点',
  TOPIC_REVIEW: '主题复习',
  DOCUMENT_REVIEW: '文档复习',
  MASTERY_REINFORCEMENT: '掌握度巩固',
  CONVERSATION_REVIEW: '会话知识复习',
};

function formatLatency(milliseconds: number) {
  return milliseconds < 1000 ? `${milliseconds} ms` : `${(milliseconds / 1000).toFixed(1)} s`;
}

function navigate(page: string) {
  window.location.hash = `/${page}`;
}

export function DashboardPage() {
  const { workspaceId = 0, user } = useAppSession();
  const [overview, setOverview] = useState<DashboardOverviewDto>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setOverview(await apiClient.get<DashboardOverviewDto>(`/v1/workspaces/${workspaceId}/dashboard`));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '工作台加载失败');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => { void loadOverview(); }, [loadOverview]);

  const knowledgeReadiness = useMemo(() => {
    const documents = overview?.recentDocuments ?? [];
    if (!documents.length) return 0;
    return Math.round(documents.filter((item) => item.ingestionStatus === 'SUCCEEDED').length * 100 / documents.length);
  }, [overview]);

  const hour = new Date().getHours();
  const greeting = hour < 11 ? '早上好' : hour < 18 ? '下午好' : '晚上好';

  return (
    <div className="page-stack dashboard-page">
      <SectionHeader
        title={`${greeting}，${user?.displayName ?? '学习者'}`}
        description="这里汇总你的知识资产状态、今日学习进度与 Agent 工作流"
        action={<Button icon={<RefreshCw size={16} />} onClick={loadOverview}>刷新数据</Button>}
      />

      <PageState loading={loading} error={error} onRetry={loadOverview}>
        <section className="dashboard-command-band">
          <div className="dashboard-focus">
            <span className="dashboard-focus__eyebrow"><Sparkles size={14} /> TODAY'S FOCUS</span>
            <h2>{overview?.dueFlashcardCount
              ? `今天有 ${overview.dueFlashcardCount} 张卡片等待复习`
              : '知识空间状态良好，可以开始新的学习任务'}</h2>
            <p>{overview?.pendingIngestionCount
              ? `另有 ${overview.pendingIngestionCount} 项资料正在摄取，完成后会自动进入知识索引。`
              : `当前已沉淀 ${overview?.knowledgeAssetCount ?? 0} 份知识资产。`}</p>
            <Button type="primary" icon={<ArrowUpRight size={16} />} onClick={() => navigate(overview?.dueFlashcardCount ? 'study' : 'ingestion')}>
              {overview?.dueFlashcardCount ? '进入今日复习' : '添加知识资料'}
            </Button>
          </div>
          <div className="readiness-gauge">
            <Progress type="dashboard" size={138} percent={knowledgeReadiness} strokeColor="#2f7772" />
            <div><strong>知识就绪度</strong><span>最近资产可检索比例</span></div>
          </div>
        </section>

        <section className="dashboard-kpi-grid">
          <article><span><LibraryBig size={19} /></span><div><small>知识资产</small><strong>{overview?.knowledgeAssetCount ?? 0}</strong><em>今日新增 {overview?.ingestedToday ?? 0}</em></div></article>
          <article><span><Clock3 size={19} /></span><div><small>摄取队列</small><strong>{overview?.pendingIngestionCount ?? 0}</strong><em>等待或处理中</em></div></article>
          <article><span><Target size={19} /></span><div><small>学习任务</small><strong>{overview?.todayPlanTaskCount ?? 0}</strong><em>到期卡片 {overview?.dueFlashcardCount ?? 0}</em></div></article>
          <article><span><BrainCircuit size={19} /></span><div><small>Agent 调用</small><strong>{overview?.agentCallCount ?? 0}</strong><em>平均 {formatLatency(overview?.averageAgentLatencyMillis ?? 0)}</em></div></article>
        </section>

        <section className="dashboard-quick-actions">
          <button type="button" onClick={() => navigate('ingestion')}><UploadCloud size={18} /><div><strong>导入资料</strong><span>上传文件或采集网页</span></div><ArrowUpRight size={15} /></button>
          <button type="button" onClick={() => navigate('knowledge')}><LibraryBig size={18} /><div><strong>浏览知识</strong><span>查看章节与重点出处</span></div><ArrowUpRight size={15} /></button>
          <button type="button" onClick={() => navigate('chat')}><MessageSquareText size={18} /><div><strong>发起问答</strong><span>从指定资料获得回答</span></div><ArrowUpRight size={15} /></button>
          <button type="button" onClick={() => navigate('study')}><GraduationCap size={18} /><div><strong>开始复习</strong><span>进入今日学习队列</span></div><ArrowUpRight size={15} /></button>
        </section>

        <div className="dashboard-content-grid">
          <section className="dashboard-feed">
            <header><div><FileText size={17} /><strong>最近知识资产</strong></div><Button type="link" onClick={() => navigate('knowledge')}>查看全部</Button></header>
            {overview?.recentDocuments.length ? (
              <div className="dashboard-document-list">
                {overview.recentDocuments.map((item) => (
                  <button key={item.id} type="button" onClick={() => navigate('knowledge')}>
                    <span className="document-type-mark">{sourceLabels[item.sourceType].slice(0, 2)}</span>
                    <div><strong>{item.title}</strong><small>{sourceLabels[item.sourceType]} · {item.chunkCount} 个片段 · {new Date(item.updatedAt).toLocaleDateString()}</small></div>
                    <Tag color={statusViews[item.ingestionStatus].color}>{statusViews[item.ingestionStatus].label}</Tag>
                  </button>
                ))}
              </div>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无知识资产" />}
          </section>

          <section className="dashboard-feed">
            <header><div><Target size={17} /><strong>今日学习路径</strong></div><Button type="link" onClick={() => navigate('study')}>进入计划</Button></header>
            {overview?.studyTasks.length ? (
              <div className="dashboard-task-list">
                {overview.studyTasks.map((task, index) => {
                  const progress = task.targetCardCount === 0
                    ? (task.completed ? 100 : 0)
                    : Math.min(100, Math.round(task.completedCardCount * 100 / task.targetCardCount));
                  return (
                    <article key={task.id}>
                      <span>{String(index + 1).padStart(2, '0')}</span>
                      <div><strong>{task.topic || studyTaskLabels[task.type]}</strong><small>{task.reason}</small><Progress percent={progress} size="small" showInfo={false} /></div>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="dashboard-empty-action">
                <GraduationCap size={28} /><strong>今天还没有学习计划</strong><span>创建计划后，任务会按优先级出现在这里。</span>
                <Button onClick={() => navigate('study')}>创建今日计划</Button>
              </div>
            )}
          </section>
        </div>
      </PageState>
    </div>
  );
}
