import { Button, Empty, Progress, Segmented, Spin, Tag, message } from 'antd';
import {
  Activity,
  BrainCircuit,
  CalendarDays,
  RefreshCw,
  Sparkles,
  Target,
  TrendingUp,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { apiClient } from '../api/client';
import type {
  ConversationLearningSummaryDto,
  LearningTopicProfileDto,
  StudyReviewStatisticsDto,
  StudyTrendDto,
} from '../api/contracts';
import { SectionHeader } from '../components/SectionHeader';
import { useAppSession } from '../contexts/AppSessionContext';

const levelView: Record<LearningTopicProfileDto['level'], { label: string; color: string }> = {
  WEAK: { label: '薄弱', color: 'red' },
  AT_RISK: { label: '需巩固', color: 'orange' },
  STABLE: { label: '稳定', color: 'blue' },
  STRONG: { label: '熟练', color: 'green' },
};

function dateText(date: Date) {
  return date.toISOString().slice(0, 10);
}

/** 面向用户的学习洞察页，替代只适合研发人员的 RAG 评估控制台。 */
export function EvaluationPage() {
  const { workspaceId = 0 } = useAppSession();
  const [range, setRange] = useState(30);
  const [trend, setTrend] = useState<StudyTrendDto>();
  const [profiles, setProfiles] = useState<LearningTopicProfileDto[]>([]);
  const [summaries, setSummaries] = useState<ConversationLearningSummaryDto[]>([]);
  const [statistics, setStatistics] = useState<StudyReviewStatisticsDto>();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - range + 1);
    try {
      const [nextTrend, nextProfiles, nextSummaries, nextStatistics] = await Promise.all([
        apiClient.get<StudyTrendDto>(`/v1/workspaces/${workspaceId}/study/analytics/trends?from=${dateText(from)}&to=${dateText(to)}`),
        apiClient.get<LearningTopicProfileDto[]>(`/v1/workspaces/${workspaceId}/study/learning-profile`),
        apiClient.get<ConversationLearningSummaryDto[]>(`/v1/workspaces/${workspaceId}/study/conversation-summaries`),
        apiClient.get<StudyReviewStatisticsDto>(`/v1/workspaces/${workspaceId}/flashcards/statistics`),
      ]);
      setTrend(nextTrend);
      setProfiles(nextProfiles);
      setSummaries(nextSummaries);
      setStatistics(nextStatistics);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '学习洞察加载失败');
    } finally {
      setLoading(false);
    }
  }, [range, workspaceId]);

  useEffect(() => { void load(); }, [load]);

  const refreshProfile = async () => {
    setRefreshing(true);
    try {
      const [nextProfiles, nextSummaries] = await Promise.all([
        apiClient.post<LearningTopicProfileDto[]>(`/v1/workspaces/${workspaceId}/study/learning-profile/refresh`, {}),
        apiClient.post<ConversationLearningSummaryDto[]>(`/v1/workspaces/${workspaceId}/study/conversation-summaries/refresh`, {}),
      ]);
      setProfiles(nextProfiles);
      setSummaries(nextSummaries);
      message.success('学习画像已根据最新记录更新');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '学习画像更新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const orderedProfiles = useMemo(
    () => [...profiles].sort((left, right) => left.masteryScore - right.masteryScore),
    [profiles],
  );
  const weakest = orderedProfiles[0];
  const recommendation = weakest
    ? `优先复习“${weakest.topic}”：当前掌握度 ${weakest.masteryScore}%，遗忘率 ${weakest.lapseRate}%。`
    : '完成几次复习后，系统会根据掌握度与遗忘率生成个性化建议。';

  if (loading) return <div className="insights-loading"><Spin size="large" /></div>;

  return (
    <div className="page-stack insights-page">
      <SectionHeader
        title="学习洞察"
        description="把复习记录、知识薄弱点与长期会话沉淀转化为下一步行动"
        action={(
          <Button icon={<RefreshCw size={16} />} loading={refreshing} onClick={refreshProfile}>
            更新学习画像
          </Button>
        )}
      />

      <section className="insight-recommendation">
        <span><Sparkles size={20} /></span>
        <div><small>AGENT 建议</small><h2>{recommendation}</h2><p>建议来自当前知识空间的复习历史与主题画像，不会跨知识空间读取数据。</p></div>
        <Button type="primary" onClick={() => { window.location.hash = '/study'; }}>开始针对性复习</Button>
      </section>

      <section className="insight-kpi-grid">
        <article><span><Activity size={18} /></span><div><small>累计复习</small><strong>{trend?.totalReviews ?? 0}</strong><em>{trend?.activeDays ?? 0} 个活跃日</em></div></article>
        <article><span><Target size={18} /></span><div><small>阶段正确率</small><strong>{trend?.accuracy ?? 0}%</strong><em>今日 {statistics?.accuracyToday ?? 0}%</em></div></article>
        <article><span><CalendarDays size={18} /></span><div><small>连续学习</small><strong>{statistics?.currentStreakDays ?? 0} 天</strong><em>保持知识节奏</em></div></article>
        <article><span><TrendingUp size={18} /></span><div><small>已覆盖卡片</small><strong>{trend?.uniqueFlashcards ?? 0}</strong><em>遗忘率 {statistics?.lapseRate ?? 0}%</em></div></article>
      </section>

      <div className="insight-main-grid">
        <section className="insight-chart-panel">
          <header><div><strong>学习趋势</strong><span>复习量与正确率变化</span></div><Segmented value={range} options={[{ label: '7 天', value: 7 }, { label: '30 天', value: 30 }, { label: '90 天', value: 90 }]} onChange={(value) => setRange(Number(value))} /></header>
          <ResponsiveContainer width="100%" height={280}>
            <AreaChart data={trend?.daily ?? []} margin={{ left: -20, right: 8 }}>
              <defs>
                <linearGradient id="reviewFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#2f7772" stopOpacity={0.24} />
                  <stop offset="100%" stopColor="#2f7772" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} stroke="#e8eeec" />
              <XAxis dataKey="date" tickFormatter={(value) => String(value).slice(5)} axisLine={false} tickLine={false} />
              <YAxis allowDecimals={false} axisLine={false} tickLine={false} />
              <Tooltip />
              <Area type="monotone" dataKey="reviewCount" name="复习次数" stroke="#2f7772" strokeWidth={2} fill="url(#reviewFill)" />
            </AreaChart>
          </ResponsiveContainer>
        </section>

        <section className="topic-mastery-panel">
          <header><strong>主题掌握度</strong><span>{profiles.length} 个学习主题</span></header>
          {orderedProfiles.length ? (
            <div className="topic-mastery-list">
              {orderedProfiles.slice(0, 8).map((profile) => (
                <article key={profile.topic}>
                  <div><strong>{profile.topic}</strong><Tag color={levelView[profile.level].color}>{levelView[profile.level].label}</Tag></div>
                  <Progress percent={Math.round(profile.masteryScore)} showInfo={false} strokeColor={profile.level === 'WEAK' ? '#d95c5c' : '#2f7772'} />
                  <span>成功率 {profile.successRate}% · 遗忘率 {profile.lapseRate}% · {profile.reviewCount} 次复习</span>
                </article>
              ))}
            </div>
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无主题画像" />}
        </section>
      </div>

      <section className="memory-insight-panel">
        <header><div><BrainCircuit size={18} /><strong>长期学习记忆</strong></div><span>从 Agent 历史会话中提取</span></header>
        {summaries.length ? (
          <div className="memory-summary-grid">
            {summaries.slice(0, 6).map((summary) => (
              <article key={summary.id}>
                <div><span>会话 #{summary.conversationId}</span><small>{summary.messageCount} 条消息</small></div>
                <p>{summary.summary}</p>
                <footer>{summary.topics.map((topic) => <Tag key={topic}>{topic}</Tag>)}</footer>
                {summary.weakTopics.length ? <em>待巩固：{summary.weakTopics.join('、')}</em> : null}
              </article>
            ))}
          </div>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="完成 Agent 对话后，这里会沉淀长期学习摘要" />}
      </section>
    </div>
  );
}
