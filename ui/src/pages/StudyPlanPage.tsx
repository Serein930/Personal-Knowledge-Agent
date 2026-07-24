import { Button, Checkbox, Empty, InputNumber, Modal, Popconfirm, Progress, Select, Space, Spin, Tag, Tooltip, message } from 'antd';
import {
  CalendarClock,
  CheckCircle2,
  Flame,
  Gauge,
  PauseCircle,
  PlayCircle,
  Plus,
  RotateCcw,
  Target,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentDto,
  DailyStudyPlanDto,
  PageResult,
  StudyFlashcardDto,
  StudyReviewSessionDto,
  StudyReviewStatisticsDto,
  SubmittedSessionReviewDto,
} from '../api/contracts';
import { MetricCard } from '../components/MetricCard';
import { ReadableText } from '../components/ReadableText';
import { SectionHeader } from '../components/SectionHeader';
import { useAppSession } from '../contexts/AppSessionContext';

const scoreLabels = ['忘记', '很难', '失败', '勉强', '记住', '轻松'];

function todayText() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function StudyPlanPage() {
  const { workspaceId = 0 } = useAppSession();
  const [statistics, setStatistics] = useState<StudyReviewStatisticsDto>();
  const [plan, setPlan] = useState<DailyStudyPlanDto>();
  const [cards, setCards] = useState<StudyFlashcardDto[]>([]);
  const [session, setSession] = useState<StudyReviewSessionDto>();
  const [dailyTarget, setDailyTarget] = useState(20);
  const [answerVisible, setAnswerVisible] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [documents, setDocuments] = useState<BackendDocumentDto[]>([]);
  const [generationOpen, setGenerationOpen] = useState(false);
  const [generationDocumentIds, setGenerationDocumentIds] = useState<number[]>([]);
  const [generationCount, setGenerationCount] = useState(5);
  const [selectedCardIds, setSelectedCardIds] = useState<number[]>([]);

  const loadWorkspace = useCallback(async () => {
    const workspacePath = `/v1/workspaces/${workspaceId}`;
    const [nextStatistics, cardPage, documentPage] = await Promise.all([
      apiClient.get<StudyReviewStatisticsDto>(`${workspacePath}/flashcards/statistics`),
      apiClient.get<PageResult<StudyFlashcardDto>>(`${workspacePath}/flashcards?page=1&pageSize=100`),
      apiClient.get<PageResult<BackendDocumentDto>>(`${workspacePath}/documents?page=1&pageSize=100&status=SUCCEEDED`),
    ]);
    setStatistics(nextStatistics);
    setCards(cardPage.records);
    setDocuments(documentPage.records);
    try {
      const nextPlan = await apiClient.get<DailyStudyPlanDto>(
        `${workspacePath}/study-plans/daily?date=${todayText()}`,
      );
      setPlan(nextPlan);
      setDailyTarget(nextPlan.dailyReviewTarget);
    } catch {
      setPlan(undefined);
    }
  }, [workspaceId]);

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
        `/v1/workspaces/${workspaceId}/study-plans/daily`,
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
        `/v1/workspaces/${workspaceId}/review-sessions`,
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
        `/v1/workspaces/${workspaceId}/review-sessions/${session.id}`
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
        `/v1/workspaces/${workspaceId}/flashcards/${card.id}/${action}`,
        { expectedVersion: card.version },
      );
      await loadWorkspace();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '卡片状态更新失败');
    } finally {
      setSubmitting(false);
    }
  };

  const generateCards = async () => {
    if (generationDocumentIds.length === 0) {
      message.warning('请至少选择一个文件或网页');
      return;
    }
    setSubmitting(true);
    try {
      const created = await apiClient.post<StudyFlashcardDto[]>(
        `/v1/workspaces/${workspaceId}/flashcards/generate-from-documents`,
        { documentIds: generationDocumentIds, count: generationCount },
      );
      setGenerationOpen(false);
      setGenerationDocumentIds([]);
      await loadWorkspace();
      message.success(`已生成 ${created.length} 张复习卡片`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复习卡片生成失败');
    } finally {
      setSubmitting(false);
    }
  };

  const deleteCard = async (card: StudyFlashcardDto) => {
    setSubmitting(true);
    try {
      await apiClient.delete<void>(`/v1/workspaces/${workspaceId}/flashcards/${card.id}`);
      await loadWorkspace();
      message.success('复习卡片已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复习卡片删除失败');
    } finally {
      setSubmitting(false);
    }
  };

  const bulkDeleteCards = async (deleteAll = false) => {
    if (!deleteAll && selectedCardIds.length === 0) return;
    setSubmitting(true);
    try {
      const result = await apiClient.post<{ deletedCount: number }>(
        `/v1/workspaces/${workspaceId}/flashcards/bulk-delete`,
        { cardIds: deleteAll ? [] : selectedCardIds, deleteAll },
      );
      setSelectedCardIds([]);
      await loadWorkspace();
      message.success(`已删除 ${result.deletedCount} 张复习卡片`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批量删除失败');
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
        description={`知识空间 ${workspaceId}`}
        action={(
          <Space>
            <Button icon={<Plus size={16} />} onClick={() => setGenerationOpen(true)}>从知识资产生成</Button>
            <Button
              type="primary"
              icon={<PlayCircle size={16} />}
              disabled={!statistics?.dueCount || session?.status === 'IN_PROGRESS'}
              loading={submitting}
              onClick={startSession}
            >
              开始复习
            </Button>
          </Space>
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
              <div className="review-question">
                <span>问题</span>
                <h3>{currentItem.flashcard.question}</h3>
              </div>
              {answerVisible ? (
                <div className="review-answer answer-evidence-stack">
                  <FlashcardAnswer answer={currentItem.flashcard.answer} />
                  {currentItem.flashcard.explanation ? (
                    <span className="review-answer__source">{currentItem.flashcard.explanation}</span>
                  ) : null}
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
        <div className="flashcard-management-header">
          <div>
            <h3>卡片管理</h3>
            <span>共 {cards.length} 张，已选择 {selectedCardIds.length} 张</span>
          </div>
          <Space>
            <Checkbox
              checked={cards.length > 0 && selectedCardIds.length === cards.length}
              indeterminate={selectedCardIds.length > 0 && selectedCardIds.length < cards.length}
              onChange={(event) => setSelectedCardIds(event.target.checked ? cards.map((card) => card.id) : [])}
            >
              全选
            </Checkbox>
            <Popconfirm
              title={`删除已选择的 ${selectedCardIds.length} 张卡片？`}
              disabled={selectedCardIds.length === 0}
              onConfirm={() => bulkDeleteCards(false)}
            >
              <Button danger disabled={selectedCardIds.length === 0} icon={<Trash2 size={15} />}>删除所选</Button>
            </Popconfirm>
            <Popconfirm
              title="清空当前知识空间的全部复习卡片？"
              description="该操作不可撤销，相关复习队列也会同步失效。"
              onConfirm={() => bulkDeleteCards(true)}
            >
              <Button danger type="primary" disabled={cards.length === 0}>清空全部</Button>
            </Popconfirm>
          </Space>
        </div>
        <div className="flashcard-management-list">
          {cards.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无复习卡片" /> : cards.map((card) => (
            <article key={card.id} className={selectedCardIds.includes(card.id) ? 'is-selected' : undefined}>
              <Checkbox
                checked={selectedCardIds.includes(card.id)}
                onChange={(event) => setSelectedCardIds((current) => (
                  event.target.checked ? [...current, card.id] : current.filter((id) => id !== card.id)
                ))}
              />
              <div>
                <strong>{card.question}</strong>
                <span>下次复习 {new Date(card.dueAt).toLocaleString()}{card.sourceDocumentId ? ` · 来源文档 ${card.sourceDocumentId}` : ''}</span>
              </div>
              <Tag color={card.status === 'SUSPENDED' ? 'default' : 'cyan'}>{card.status}</Tag>
              <Space size={4}>
                <Tooltip title={card.status === 'SUSPENDED' ? '恢复卡片' : '暂停卡片'}>
                  <Button
                    aria-label={card.status === 'SUSPENDED' ? '恢复卡片' : '暂停卡片'}
                    icon={card.status === 'SUSPENDED' ? <PlayCircle size={16} /> : <PauseCircle size={16} />}
                    loading={submitting}
                    onClick={() => changeCardStatus(card)}
                  />
                </Tooltip>
                <Popconfirm title="删除这张复习卡片？" okText="删除" cancelText="取消" onConfirm={() => deleteCard(card)}>
                  <Tooltip title="删除卡片"><Button aria-label="删除卡片" danger icon={<Trash2 size={16} />} /></Tooltip>
                </Popconfirm>
              </Space>
            </article>
          ))}
        </div>
      </section>

      <Modal
        title="从指定知识资产生成复习卡片"
        open={generationOpen}
        okText="生成卡片"
        cancelText="取消"
        confirmLoading={submitting}
        okButtonProps={{ disabled: generationDocumentIds.length === 0 }}
        onOk={generateCards}
        onCancel={() => setGenerationOpen(false)}
      >
        <div className="form-stack">
          <p className="form-hint">系统会从所选资料中自动提取具体问题，并为每个问题生成一条简短答案。</p>
          <label>
            <span>选择文件或网页</span>
            <Select
              mode="multiple"
              value={generationDocumentIds}
              placeholder="请选择已完成摄取的知识资产"
              options={documents.map((document) => ({ value: document.id, label: document.title }))}
              onChange={setGenerationDocumentIds}
            />
          </label>
          <label>
            <span>生成数量</span>
            <InputNumber min={1} max={20} value={generationCount} onChange={(value) => setGenerationCount(value ?? 5)} />
          </label>
        </div>
      </Modal>
    </div>
  );
}

function FlashcardAnswer({ answer }: { answer: string }) {
  const sourceMarker = '【资料依据】';
  const supplementMarker = '【联网补充】';
  if (!answer.includes(sourceMarker)) {
    return (
      <section className="answer-evidence answer-evidence--source">
        <strong>资料依据</strong>
        <ReadableText className="readable-text" content={answer} />
      </section>
    );
  }
  const sourceStart = answer.indexOf(sourceMarker) + sourceMarker.length;
  const supplementStart = answer.indexOf(supplementMarker);
  const sourceAnswer = answer.slice(sourceStart, supplementStart > -1 ? supplementStart : undefined).trim();
  const supplement = supplementStart > -1
    ? answer.slice(supplementStart + supplementMarker.length).trim() : '';
  return (
    <>
      <section className="answer-evidence answer-evidence--source">
        <strong>知识库资料依据</strong>
        <ReadableText className="readable-text" content={sourceAnswer} />
      </section>
      {supplement ? (
        <section className="answer-evidence answer-evidence--web">
          <strong>联网搜索补充</strong>
          <ReadableText className="readable-text" content={supplement} />
        </section>
      ) : null}
    </>
  );
}
