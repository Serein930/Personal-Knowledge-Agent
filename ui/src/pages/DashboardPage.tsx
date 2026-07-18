import { Button, Empty, Progress, Tag } from 'antd';
import { BrainCircuit, Clock3, FileText, RefreshCw, Target } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentSourceType,
  BackendIngestionStatus,
  DashboardOverviewDto,
  DailyStudyPlanDto,
} from '../api/contracts';
import { MetricCard } from '../components/MetricCard';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';

const sourceLabels: Record<BackendDocumentSourceType, string> = {
  PDF: 'PDF',
  MARKDOWN: 'Markdown',
  WEB_PAGE: '网页文章',
  WORD: 'Word',
  TEXT: '文本',
  CODE: '代码',
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
  if (milliseconds < 1000) return `${milliseconds} 毫秒`;
  return `${(milliseconds / 1000).toFixed(1)} 秒`;
}

export function DashboardPage() {
  const [overview, setOverview] = useState<DashboardOverviewDto>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const result = await apiClient.get<DashboardOverviewDto>(
        `/v1/workspaces/${env.workspaceId}/dashboard`,
      );
      setOverview(result);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '工作台加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  return (
    <div className="page-stack">
      <SectionHeader
        title="工作台"
        description={`知识空间 ${env.workspaceId} 的知识、学习与 Agent 运行概览。`}
        action={<Button icon={<RefreshCw size={16} />} onClick={loadOverview}>刷新</Button>}
      />

      <PageState loading={loading} error={error} onRetry={loadOverview}>
        <div className="metric-grid">
          <MetricCard
            label="知识资产"
            value={String(overview?.knowledgeAssetCount ?? 0)}
            hint="当前知识空间文档总数"
            icon={<FileText size={20} />}
          />
          <MetricCard
            label="今日摄取"
            value={String(overview?.ingestedToday ?? 0)}
            hint={`等待或处理中 ${overview?.pendingIngestionCount ?? 0} 项`}
            icon={<Clock3 size={20} />}
          />
          <MetricCard
            label="今日学习任务"
            value={String(overview?.todayPlanTaskCount ?? 0)}
            hint={`当前到期 ${overview?.dueFlashcardCount ?? 0} 张卡片`}
            icon={<Target size={20} />}
          />
          <MetricCard
            label="Agent 调用"
            value={String(overview?.agentCallCount ?? 0)}
            hint={`平均响应 ${formatLatency(overview?.averageAgentLatencyMillis ?? 0)}`}
            icon={<BrainCircuit size={20} />}
          />
        </div>

        <div className="two-column">
          <section className="panel">
            <h3>最近知识资产</h3>
            {overview?.recentDocuments.length ? (
              <div className="compact-list">
                {overview.recentDocuments.map((item) => (
                  <article key={item.id}>
                    <strong>{item.title}</strong>
                    <span>
                      {sourceLabels[item.sourceType]} · {item.workspaceName} ·{' '}
                      <Tag color={statusViews[item.ingestionStatus].color}>
                        {statusViews[item.ingestionStatus].label}
                      </Tag>
                    </span>
                  </article>
                ))}
              </div>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无知识资产" />}
          </section>

          <section className="panel">
            <h3>今日学习进度</h3>
            {overview?.studyTasks.length ? (
              <div className="compact-list">
                {overview.studyTasks.map((task) => {
                  const progress = task.targetCardCount === 0
                    ? (task.completed ? 100 : 0)
                    : Math.min(100, Math.round(task.completedCardCount * 100 / task.targetCardCount));
                  return (
                    <article key={task.id}>
                      <strong>{task.topic || studyTaskLabels[task.type]}</strong>
                      <span>{task.reason}</span>
                      <Progress percent={progress} size="small" showInfo={false} />
                    </article>
                  );
                })}
              </div>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="今日尚未创建学习计划" />}
          </section>
        </div>
      </PageState>
    </div>
  );
}
