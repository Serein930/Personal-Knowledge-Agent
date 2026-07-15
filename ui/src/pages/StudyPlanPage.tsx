import { Button, Empty, InputNumber, Progress, Spin, Tag, Tooltip, message } from 'antd';
import {
  CalendarClock,
  CheckCircle2,
  Flame,
  Gauge,
  PauseCircle,
  PlayCircle,
  RotateCcw,
  Target,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  DailyStudyPlanDto,
  PageResult,
  StudyFlashcardDto,
  StudyReviewSessionDto,
  StudyReviewStatisticsDto,
  SubmittedSessionReviewDto,
} from '../api/contracts';
import { MetricCard } from '../components/MetricCard';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';

const scoreLabels = ['忘记', '很难', '失败', '勉强', '记住', '轻松'];

function todayText() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function StudyPlanPage() {
  const [statistics, setStatistics] = useState<StudyReviewStatisticsDto>();
  const [plan, setPlan] = useState<DailyStudyPlanDto>();
  const [cards, setCards] = useState<StudyFlashcardDto[]>([]);
  const [session, setSession] = useState<StudyReviewSessionDto>();
  const [dailyTarget, setDailyTarget] = useState(20);
  const [answerVisible, setAnswerVisible] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const loadWorkspace = useCallback(async () => {
    const workspacePath = `/v1/workspaces/${env.workspaceId}`;
    const [nextStatistics, cardPage] = await Promise.all([
      apiClient.get<StudyReviewStatisticsDto>(`${workspacePath}/flashcards/statistics`),
      apiClient.get<PageResult<StudyFlashcardDto>>(`${workspacePath}/flashcards?page=1&pageSize=100`),
    ]);
    setStatistics(nextStatistics);
    setCards(cardPage.records);
    try {
      const nextPlan = await apiClient.get<DailyStudyPlanDto>(
        `${workspacePath}/study-plans/daily?date=${todayText()}`,
      );
      setPlan(nextPlan);
      setDailyTarget(nextPlan.dailyReviewTarget);
    } catch {
      setPlan(undefined);
    }
  }, []);

  useEffect(() => {
    void loadWorkspace()
      .catch((error) => message.error(error instanceof Error ? error.message : '学习数据加载失败'))
      .finally(() => setLoading(false));
  }, [loadWorkspace]);

  const currentItem = useMemo(
    () => session?.queue.find((item) => item.status === 'PENDING'),
    [session],
  );

  const savePlan = async () => {
    setSubmitting(true);
    try {
      const result = await apiClient.post<DailyStudyPlanDto>(
        `/v1/workspaces/${env.workspaceId}/study-plans/daily`,
        { planDate: todayText(), dailyReviewTarget: dailyTarget },
      );
      setPlan(result);
      message.success('今日目标已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '学习计划保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const startSession = async () => {
    if (!statistics?.dueCount) return;
    setSubmitting(true);
    try {
      const result = await apiClient.post<StudyReviewSessionDto>(
        `/v1/workspaces/${env.workspaceId}/review-sessions`,
        { limit: Math.min(100, Math.max(1, dailyTarget)) },
      );
      setSession(result);
      setAnswerVisible(false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复习会话创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  const submitScore = async (score: number) => {
    if (!session || !currentItem) return;
    setSubmitting(true);
    try {
      const requestId = globalThis.crypto?.randomUUID?.() ?? `review-${Date.now()}`;
      const result = await apiClient.post<SubmittedSessionReviewDto>(
        `/v1/workspaces/${env.workspaceId}/review-sessions/${session.id}`
          + `/cards/${currentItem.flashcard.id}/reviews`,
        { requestId, score },
      );
      setSession(result.session);
      setAnswerVisible(false);
      await loadWorkspace();
      if (result.session.status === 'COMPLETED') message.success('本次复习已完成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评分提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  const changeCardStatus = async (card: StudyFlashcardDto) => {
    setSubmitting(true);
    const action = card.status === 'SUSPENDED' ? 'resume' : 'suspend';
    try {
      await apiClient.post<StudyFlashcardDto>(
        `/v1/workspaces/${env.workspaceId}/flashcards/${card.id}/${action}`,
        { expectedVersion: card.version },
      );
      await loadWorkspace();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '卡片状态更新失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="study-loading"><Spin size="large" /></div>;
  }

  return (
    <div className="page-stack">
      <SectionHeader
        title="复习工作台"
        description={`知识空间 ${env.workspaceId}`}
        action={(
          <Button
            type="primary"
            icon={<PlayCircle size={16} />}
            disabled={!statistics?.dueCount || session?.status === 'IN_PROGRESS'}
            loading={submitting}
            onClick={startSession}
          >
            开始复习
          </Button>
        )}
      />

      <div className="metric-grid">
        <MetricCard label="当前到期" value={String(statistics?.dueCount ?? 0)} hint="待进入复习队列" icon={<CalendarClock size={20} />} />
        <MetricCard label="今日完成" value={String(statistics?.completedToday ?? 0)} hint={`正确率 ${statistics?.accuracyToday ?? 0}%`} icon={<CheckCircle2 size={20} />} />
        <MetricCard label="连续学习" value={`${statistics?.currentStreakDays ?? 0} 天`} hint="按本地学习时区统计" icon={<Flame size={20} />} />
        <MetricCard label="遗忘率" value={`${statistics?.lapseRate ?? 0}%`} hint={`累计评分 ${statistics?.totalReviews ?? 0} 次`} icon={<Gauge size={20} />} />
      </div>

      <div className="study-workspace-grid">
        <section className="panel review-stage">
          <div className="item-line">
            <h3>复习队列</h3>
            {session ? <Tag color={session.status === 'COMPLETED' ? 'green' : 'blue'}>{session.reviewedCards}/{session.totalCards}</Tag> : null}
          </div>
          {session ? <Progress percent={session.progress} showInfo={false} /> : null}

          {!currentItem ? (
            <Empty description={session?.status === 'COMPLETED' ? '本次复习已完成' : '尚未开始复习'} />
          ) : (
            <div className="review-card">
              <span className="review-card__position">第 {currentItem.position} 张</span>
              <h3>{currentItem.flashcard.question}</h3>
              {answerVisible ? (
                <div className="review-answer">
                  <strong>答案</strong>
                  <p>{currentItem.flashcard.answer}</p>
                  {currentItem.flashcard.explanation ? <span>{currentItem.flashcard.explanation}</span> : null}
                </div>
              ) : (
                <Button icon={<RotateCcw size={16} />} onClick={() => setAnswerVisible(true)}>显示答案</Button>
              )}
              {answerVisible ? (
                <div className="score-grid">
                  {scoreLabels.map((label, score) => (
                    <Button key={label} loading={submitting} onClick={() => submitScore(score)}>
                      <strong>{score}</strong><span>{label}</span>
                    </Button>
                  ))}
                </div>
              ) : null}
            </div>
          )}
        </section>

        <aside className="panel daily-plan-panel">
          <h3>今日计划</h3>
          <div className="daily-target-row">
            <InputNumber min={1} max={500} value={dailyTarget} onChange={(value) => setDailyTarget(value ?? 20)} />
            <Button type="primary" icon={<Target size={16} />} loading={submitting} onClick={savePlan}>保存目标</Button>
          </div>
          {plan ? (
            <div className="plan-progress">
              <Progress type="circle" size={128} percent={plan.progress} />
              <div>
                <strong>{plan.completedReviews} / {plan.dailyReviewTarget}</strong>
                <span>剩余 {plan.remainingReviews} 张</span>
                <span>计划创建时到期 {plan.dueCardSnapshot} 张</span>
              </div>
            </div>
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="今日暂无计划" />}
        </aside>
      </div>

      <div className="two-column">
        <section className="panel">
          <h3>评分分布</h3>
          <div className="score-distribution">
            {statistics?.scoreDistribution.map((bucket) => {
              const maximum = Math.max(1, ...statistics.scoreDistribution.map((item) => item.count));
              return (
                <div key={bucket.score}>
                  <span>{bucket.score} · {scoreLabels[bucket.score]}</span>
                  <div className="progress-track"><div style={{ width: `${bucket.count * 100 / maximum}%` }} /></div>
                  <strong>{bucket.count}</strong>
                </div>
              );
            })}
          </div>
        </section>

        <section className="panel">
          <h3>卡片成熟度</h3>
          <div className="maturity-grid">
            <div><strong>{statistics?.maturity.newCount ?? 0}</strong><span>新卡片</span></div>
            <div><strong>{statistics?.maturity.learningCount ?? 0}</strong><span>学习中</span></div>
            <div><strong>{statistics?.maturity.youngCount ?? 0}</strong><span>年轻卡片</span></div>
            <div><strong>{statistics?.maturity.matureCount ?? 0}</strong><span>成熟卡片</span></div>
          </div>
        </section>
      </div>

      <section className="panel">
        <h3>卡片管理</h3>
        <div className="flashcard-management-list">
          {cards.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无复习卡片" /> : cards.map((card) => (
            <article key={card.id}>
              <div><strong>{card.question}</strong><span>下次复习 {new Date(card.dueAt).toLocaleString()}</span></div>
              <Tag color={card.status === 'SUSPENDED' ? 'default' : 'cyan'}>{card.status}</Tag>
              <Tooltip title={card.status === 'SUSPENDED' ? '恢复卡片' : '暂停卡片'}>
                <Button
                  aria-label={card.status === 'SUSPENDED' ? '恢复卡片' : '暂停卡片'}
                  icon={card.status === 'SUSPENDED' ? <PlayCircle size={16} /> : <PauseCircle size={16} />}
                  loading={submitting}
                  onClick={() => changeCardStatus(card)}
                />
              </Tooltip>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
