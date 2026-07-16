import {
  Alert,
  Button,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Segmented,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
  Upload,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BarChart3,
  Clock3,
  Coins,
  Download,
  FileUp,
  GitCompareArrows,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Square,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { apiClient, buildApiUrl } from '../api/client';
import type {
  PageResult,
  RagEvaluationCaseDiffType,
  RagEvaluationCaseDto,
  RagEvaluationCaseResultDto,
  RagEvaluationComparisonDto,
  RagEvaluationDashboardDto,
  RagEvaluationDatasetDto,
  RagEvaluationDatasetVersionDto,
  RagEvaluationJobDto,
  RagEvaluationJobStatus,
  RagEvaluationRerankStrategy,
  RagEvaluationRetrievalStrategy,
  RagEvaluationTrendDto,
  RagEvaluationVersionDiffDto,
} from '../api/contracts';
import { MetricCard } from '../components/MetricCard';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';

const sampleCases: RagEvaluationCaseDto[] = [
  {
    caseKey: 'java-virtual-thread',
    question: '资料中如何解释 Java 虚拟线程的适用场景？',
    expectedRelevantChunkIds: ['请替换为知识库中的片段编号'],
    expectedRelevantDocumentIds: [],
    expectedRefusal: false,
    expectedAnswerKeywords: ['虚拟线程', '阻塞'],
  },
  {
    caseKey: 'out-of-scope-refusal',
    question: '资料中没有提到的主题是否应该拒绝回答？',
    expectedRelevantChunkIds: [],
    expectedRelevantDocumentIds: [],
    expectedRefusal: true,
    expectedAnswerKeywords: [],
  },
];

const statusView: Record<RagEvaluationJobStatus, { label: string; color: string }> = {
  PENDING: { label: '排队中', color: 'default' },
  RUNNING: { label: '运行中', color: 'processing' },
  CANCEL_REQUESTED: { label: '取消中', color: 'warning' },
  CANCELED: { label: '已取消', color: 'default' },
  SUCCEEDED: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
};

const diffView: Record<RagEvaluationCaseDiffType, { label: string; color: string }> = {
  ADDED: { label: '新增', color: 'success' },
  REMOVED: { label: '删除', color: 'error' },
  MODIFIED: { label: '修改', color: 'warning' },
  UNCHANGED: { label: '未变', color: 'default' },
};

interface DatasetFormValues {
  name?: string;
  description?: string;
  changeNote?: string;
  casesJson: string;
}

function percentage(value?: number) {
  return `${(value ?? 0).toFixed(2)}%`;
}

function signed(value?: number, suffix = '') {
  if (value === undefined) return '暂无基线';
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}${suffix}`;
}

function wait(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

export function EvaluationPage() {
  const [form] = Form.useForm<DatasetFormValues>();
  const [dashboard, setDashboard] = useState<RagEvaluationDashboardDto>();
  const [datasets, setDatasets] = useState<RagEvaluationDatasetDto[]>([]);
  const [versions, setVersions] = useState<RagEvaluationDatasetVersionDto[]>([]);
  const [trend, setTrend] = useState<RagEvaluationTrendDto>();
  const [versionDiff, setVersionDiff] = useState<RagEvaluationVersionDiffDto>();
  const [selectedDatasetId, setSelectedDatasetId] = useState<number>();
  const [selectedVersion, setSelectedVersion] = useState<number>();
  const [selectedJob, setSelectedJob] = useState<RagEvaluationJobDto>();
  const [selectedComparison, setSelectedComparison] = useState<RagEvaluationComparisonDto>();
  const [experimentName, setExperimentName] = useState('候选策略对比实验');
  const [retrievalStrategy, setRetrievalStrategy] = useState<RagEvaluationRetrievalStrategy>('VECTOR');
  const [rerankStrategy, setRerankStrategy] = useState<RagEvaluationRerankStrategy>('NONE');
  const [topK, setTopK] = useState(5);
  const [candidatePoolSize, setCandidatePoolSize] = useState(20);
  const [minimumRecall, setMinimumRecall] = useState<number>();
  const [minimumNdcg, setMinimumNdcg] = useState<number>();
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [creating, setCreating] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'dataset' | 'version'>('dataset');

  const workspacePath = `/v1/workspaces/${env.workspaceId}/evaluations`;

  const loadOverview = useCallback(async () => {
    const [nextDashboard, datasetPage] = await Promise.all([
      apiClient.get<RagEvaluationDashboardDto>(`${workspacePath}/dashboard`),
      apiClient.get<PageResult<RagEvaluationDatasetDto>>(`${workspacePath}/datasets?page=1&pageSize=100`),
    ]);
    setDashboard(nextDashboard);
    setDatasets(datasetPage.records);
    setSelectedJob((current) => {
      const refreshed = current && nextDashboard.recentJobs.find((job) => job.id === current.id);
      return refreshed ?? current ?? nextDashboard.latestSuccessfulJob ?? nextDashboard.recentJobs[0];
    });
    if (!selectedDatasetId && datasetPage.records.length > 0) {
      setSelectedDatasetId(datasetPage.records[0].id);
    }
  }, [selectedDatasetId, workspacePath]);

  const loadVersions = useCallback(async (datasetId: number) => {
    const result = await apiClient.get<RagEvaluationDatasetVersionDto[]>(
      `${workspacePath}/datasets/${datasetId}/versions`,
    );
    setVersions(result);
    setSelectedVersion((current) => result.some((item) => item.version === current) ? current : result[0]?.version);
  }, [workspacePath]);

  const loadAnalysis = useCallback(async (datasetId: number, currentVersions: RagEvaluationDatasetVersionDto[]) => {
    const nextTrend = await apiClient.get<RagEvaluationTrendDto>(
      `${workspacePath}/datasets/${datasetId}/trends?limit=30`,
    );
    setTrend(nextTrend);
    if (currentVersions.length >= 2) {
      const [latest, previous] = currentVersions;
      setVersionDiff(await apiClient.get<RagEvaluationVersionDiffDto>(
        `${workspacePath}/datasets/${datasetId}/versions/diff?fromVersion=${previous.version}&toVersion=${latest.version}`,
      ));
    } else {
      setVersionDiff(undefined);
    }
  }, [workspacePath]);

  useEffect(() => {
    void loadOverview()
      .catch((error) => message.error(error instanceof Error ? error.message : '评估面板加载失败'))
      .finally(() => setLoading(false));
  }, [loadOverview]);

  useEffect(() => {
    if (!selectedDatasetId) {
      setVersions([]);
      setSelectedVersion(undefined);
      setTrend(undefined);
      return;
    }
    void loadVersions(selectedDatasetId).catch((error) => {
      message.error(error instanceof Error ? error.message : '评估集版本加载失败');
    });
  }, [loadVersions, selectedDatasetId]);

  useEffect(() => {
    if (!selectedDatasetId || versions.length === 0) return;
    void loadAnalysis(selectedDatasetId, versions).catch((error) => {
      message.error(error instanceof Error ? error.message : '评估分析数据加载失败');
    });
  }, [loadAnalysis, selectedDatasetId, versions]);

  useEffect(() => {
    if (!selectedJob || selectedJob.status !== 'SUCCEEDED' || !selectedJob.baselineJobId) {
      setSelectedComparison(undefined);
      return;
    }
    void apiClient.get<RagEvaluationComparisonDto>(
      `${workspacePath}/jobs/${selectedJob.id}/comparison`,
    ).then(setSelectedComparison).catch(() => setSelectedComparison(undefined));
  }, [selectedJob, workspacePath]);

  const latestMetrics = dashboard?.latestSuccessfulJob?.metrics;
  const delta = dashboard?.latestComparison?.comparable ? dashboard.latestComparison.delta : undefined;

  const submitDatasetForm = async () => {
    const values = await form.validateFields();
    let cases: RagEvaluationCaseDto[];
    try {
      cases = JSON.parse(values.casesJson) as RagEvaluationCaseDto[];
      if (!Array.isArray(cases) || cases.length === 0) throw new Error();
    } catch {
      message.error('评估题目必须是非空 JSON 数组');
      return;
    }
    setCreating(true);
    try {
      const created = modalMode === 'dataset'
        ? await apiClient.post<RagEvaluationDatasetVersionDto>(`${workspacePath}/datasets`, {
          name: values.name,
          description: values.description ?? '',
          cases,
        })
        : await apiClient.post<RagEvaluationDatasetVersionDto>(
          `${workspacePath}/datasets/${selectedDatasetId}/versions`,
          { changeNote: values.changeNote ?? '', cases },
        );
      setModalOpen(false);
      form.resetFields();
      await loadOverview();
      setSelectedDatasetId(created.datasetId);
      await loadVersions(created.datasetId);
      setSelectedVersion(created.version);
      message.success(modalMode === 'dataset' ? '固定评估集第 1 版已创建' : `固定评估集 v${created.version} 已创建`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评估集创建失败');
    } finally {
      setCreating(false);
    }
  };

  const openDatasetModal = () => {
    setModalMode('dataset');
    form.setFieldsValue({
      name: '', description: '', changeNote: '', casesJson: JSON.stringify(sampleCases, null, 2),
    });
    setModalOpen(true);
  };

  const openVersionModal = () => {
    const currentVersion = versions.find((version) => version.version === selectedVersion) ?? versions[0];
    if (!selectedDatasetId || !currentVersion) {
      message.warning('请先选择一个评估集版本');
      return;
    }
    setModalMode('version');
    form.setFieldsValue({
      name: '', description: '', changeNote: '',
      casesJson: JSON.stringify(currentVersion.cases, null, 2),
    });
    setModalOpen(true);
  };

  // 轮询只读取单个任务，不反复刷新整个仪表盘，终态后再统一刷新统计和趋势。
  const awaitJob = async (jobId: number) => {
    for (let attempt = 0; attempt < 240; attempt += 1) {
      const current = await apiClient.get<RagEvaluationJobDto>(`${workspacePath}/jobs/${jobId}`);
      setSelectedJob(current);
      if (current.terminal) return current;
      await wait(250);
    }
    throw new Error('评估任务仍在运行，请稍后刷新查看');
  };

  const runEvaluation = async () => {
    if (!selectedDatasetId || !selectedVersion) {
      message.warning('请先选择评估集和版本');
      return;
    }
    if (candidatePoolSize < topK) {
      message.warning('候选池大小不能小于 TopK');
      return;
    }
    setRunning(true);
    try {
      const submitted = await apiClient.post<RagEvaluationJobDto>(`${workspacePath}/jobs`, {
        datasetId: selectedDatasetId,
        datasetVersion: selectedVersion,
        experimentName,
        retrievalStrategy,
        candidatePoolSize,
        rerankStrategy,
        topK,
        qualityGate: {
          minimumRecallAtK: minimumRecall,
          minimumNdcgAtK: minimumNdcg,
        },
      });
      setSelectedJob(submitted);
      message.info(`评估任务 #${submitted.id} 已进入队列`);
      const completed = await awaitJob(submitted.id);
      await loadOverview();
      if (selectedDatasetId) await loadAnalysis(selectedDatasetId, versions);
      if (completed.status === 'FAILED') message.error(completed.failureReason || '评估任务执行失败');
      else if (completed.status === 'CANCELED') message.warning(`评估任务 #${completed.id} 已取消`);
      else message.success(`评估任务 #${completed.id} 已完成`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评估任务启动失败');
    } finally {
      setRunning(false);
    }
  };

  const cancelJob = async (job: RagEvaluationJobDto) => {
    const current = await apiClient.post<RagEvaluationJobDto>(`${workspacePath}/jobs/${job.id}/cancel`, {});
    setSelectedJob(current);
    await loadOverview();
    message.success(current.status === 'CANCELED' ? '评估任务已取消' : '已提交取消请求');
  };

  const retryJob = async (job: RagEvaluationJobDto) => {
    setRunning(true);
    try {
      const retry = await apiClient.post<RagEvaluationJobDto>(`${workspacePath}/jobs/${job.id}/retry`, {});
      setSelectedJob(retry);
      message.info(`重试任务 #${retry.id} 已进入队列`);
      await awaitJob(retry.id);
      await loadOverview();
    } finally {
      setRunning(false);
    }
  };

  const importDataset = async (file: File) => {
    const format = file.name.toLowerCase().endsWith('.csv') ? 'CSV'
      : file.name.toLowerCase().endsWith('.json') ? 'JSON' : undefined;
    if (!format) {
      message.error('只支持 JSON 或 CSV 评估集文件');
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    try {
      const created = await apiClient.upload<RagEvaluationDatasetVersionDto>(
        `${workspacePath}/datasets/import?format=${format}`,
        formData,
      );
      await loadOverview();
      setSelectedDatasetId(created.datasetId);
      message.success(`评估集已导入，共 ${created.cases.length} 道题`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评估集导入失败');
    }
  };

  const exportDataset = async (format: 'JSON' | 'CSV') => {
    if (!selectedDatasetId || !selectedVersion) {
      message.warning('请先选择评估集和版本');
      return;
    }
    const response = await fetch(buildApiUrl(
      `${workspacePath}/datasets/${selectedDatasetId}/versions/${selectedVersion}/export?format=${format}`,
    ));
    if (!response.ok) {
      message.error('评估集导出失败');
      return;
    }
    const blobUrl = URL.createObjectURL(await response.blob());
    const anchor = document.createElement('a');
    anchor.href = blobUrl;
    anchor.download = `rag-evaluation-${selectedDatasetId}-v${selectedVersion}.${format.toLowerCase()}`;
    anchor.click();
    URL.revokeObjectURL(blobUrl);
  };

  const jobColumns: ColumnsType<RagEvaluationJobDto> = [
    { title: '任务', dataIndex: 'id', width: 80, render: (value: number) => `#${value}` },
    { title: '实验', key: 'experiment', width: 180, render: (_, row) => row.experimentConfig.experimentName },
    { title: '版本', key: 'version', width: 110, render: (_, row) => `${row.datasetId} / v${row.datasetVersion}` },
    { title: '状态', dataIndex: 'status', width: 100, render: (status: RagEvaluationJobStatus) => <Tag color={statusView[status].color}>{statusView[status].label}</Tag> },
    { title: '进度', dataIndex: 'progress', width: 150, render: (value: number, row) => <Progress percent={value} size="small" status={row.status === 'FAILED' ? 'exception' : undefined} /> },
    { title: '策略', key: 'strategy', width: 140, render: (_, row) => `${row.experimentConfig.retrievalStrategy} / ${row.experimentConfig.rerankStrategy}` },
    { title: 'NDCG@K', key: 'ndcg', width: 100, render: (_, row) => row.metrics ? percentage(row.metrics.ndcgAtK) : '-' },
    { title: '平均耗时', key: 'latency', width: 110, render: (_, row) => row.metrics ? `${row.metrics.averageLatencyMillis} ms` : '-' },
    {
      title: '操作', key: 'actions', width: 88, fixed: 'right', render: (_, row) => (
        <div className="evaluation-table-actions" onClick={(event) => event.stopPropagation()}>
          {['PENDING', 'RUNNING', 'CANCEL_REQUESTED'].includes(row.status) ? (
            <Tooltip title="取消任务"><Button type="text" danger icon={<Square size={16} />} onClick={() => void cancelJob(row)} /></Tooltip>
          ) : null}
          {['FAILED', 'CANCELED'].includes(row.status) ? (
            <Tooltip title="按原配置重试"><Button type="text" icon={<RotateCcw size={16} />} onClick={() => void retryJob(row)} /></Tooltip>
          ) : null}
        </div>
      ),
    },
  ];

  const caseColumns: ColumnsType<RagEvaluationCaseResultDto> = [
    { title: '题目标识', dataIndex: 'caseKey', width: 170 },
    { title: 'Recall@K', dataIndex: 'recallAtK', render: percentage },
    { title: 'NDCG@K', dataIndex: 'ndcgAtK', render: percentage },
    {
      title: '基线 ΔNDCG',
      key: 'ndcgDelta',
      render: (_, row) => {
        const value = selectedComparison?.caseDeltas.find((item) => item.caseKey === row.caseKey)?.ndcgAtK;
        return value === undefined ? '-' : signed(value, ' 个百分点');
      },
    },
    { title: '忠实度', dataIndex: 'faithfulness', render: percentage },
    { title: '答案相关性', dataIndex: 'answerRelevance', render: percentage },
    { title: '首个相关排名', dataIndex: 'firstRelevantRank', render: (value?: number) => value ?? '-' },
    { title: '引用', dataIndex: 'citationCovered', render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '覆盖' : '未覆盖'}</Tag> },
    { title: '拒答判断', dataIndex: 'refusalCorrect', render: (value: boolean) => <Tag color={value ? 'success' : 'error'}>{value ? '正确' : '错误'}</Tag> },
    { title: '检索/重排/生成', key: 'phases', width: 180, render: (_, row) => row.phaseTiming ? `${row.phaseTiming.retrievalMillis}/${row.phaseTiming.rerankMillis}/${row.phaseTiming.generationMillis} ms` : '-' },
    { title: 'Token', key: 'tokens', render: (_, row) => row.promptTokens + row.completionTokens },
  ];

  const diffColumns: ColumnsType<RagEvaluationVersionDiffDto['cases'][number]> = [
    { title: '题目标识', dataIndex: 'caseKey' },
    { title: '变化', dataIndex: 'type', width: 100, render: (type: RagEvaluationCaseDiffType) => <Tag color={diffView[type].color}>{diffView[type].label}</Tag> },
    { title: '变更后问题', key: 'question', render: (_, row) => row.after?.question ?? row.before?.question ?? '-' },
  ];

  if (loading) return <div className="study-loading"><Spin size="large" /></div>;

  return (
    <div className="page-stack">
      <SectionHeader
        title="RAG 评估工作台"
        description={`知识空间 ${env.workspaceId} · 可重复实验、质量门禁与逐题证据`}
        action={(
          <div className="evaluation-header-actions">
            <Tooltip title="刷新评估数据"><Button icon={<RefreshCw size={16} />} onClick={() => void loadOverview()} /></Tooltip>
            <Upload accept=".json,.csv" showUploadList={false} beforeUpload={(file) => { void importDataset(file); return Upload.LIST_IGNORE; }}>
              <Button icon={<FileUp size={16} />}>导入</Button>
            </Upload>
            <Dropdown menu={{ items: [
              { key: 'JSON', label: '导出 JSON', onClick: () => void exportDataset('JSON') },
              { key: 'CSV', label: '导出 CSV', onClick: () => void exportDataset('CSV') },
            ] }}>
              <Button icon={<Download size={16} />} disabled={!selectedDatasetId}>导出</Button>
            </Dropdown>
            <Button icon={<Plus size={16} />} disabled={!selectedDatasetId} onClick={openVersionModal}>新版本</Button>
            <Button type="primary" icon={<Plus size={16} />} onClick={openDatasetModal}>新建评估集</Button>
          </div>
        )}
      />

      <div className="evaluation-metric-grid">
        <MetricCard label="Recall@K" value={percentage(latestMetrics?.recallAtK)} hint={signed(delta?.recallAtK, ' 个百分点')} icon={<BarChart3 size={20} />} />
        <MetricCard label="NDCG@K" value={percentage(latestMetrics?.ndcgAtK)} hint={signed(delta?.ndcgAtK, ' 个百分点')} icon={<GitCompareArrows size={20} />} />
        <MetricCard label="忠实度" value={percentage(latestMetrics?.faithfulness)} hint={signed(delta?.faithfulness, ' 个百分点')} icon={<ShieldCheck size={20} />} />
        <MetricCard label="答案相关性" value={percentage(latestMetrics?.answerRelevance)} hint={signed(delta?.answerRelevance, ' 个百分点')} icon={<BarChart3 size={20} />} />
        <MetricCard label="平均耗时" value={`${latestMetrics?.averageLatencyMillis ?? 0} ms`} hint={signed(delta?.averageLatencyMillis, ' ms')} icon={<Clock3 size={20} />} />
        <MetricCard label="Token / 成本" value={String(latestMetrics?.totalTokens ?? 0)} hint={`$${(latestMetrics?.estimatedCostUsd ?? 0).toFixed(6)}${latestMetrics?.tokenUsageEstimated ? ' · 估算' : ''}`} icon={<Coins size={20} />} />
      </div>

      <section className="panel evaluation-experiment-panel">
        <div className="item-line">
          <div><h3>运行可重复实验</h3><p className="muted">任务会冻结数据集、chunk、检索、重排、提示词和模型配置。</p></div>
          <Button type="primary" icon={<Play size={16} />} loading={running} disabled={datasets.length === 0} onClick={() => void runEvaluation()}>开始评估</Button>
        </div>
        <div className="evaluation-run-grid">
          <label className="evaluation-field"><span>实验名称</span><Input value={experimentName} maxLength={120} onChange={(event) => setExperimentName(event.target.value)} /></label>
          <Select aria-label="选择评估集" placeholder="选择评估集" value={selectedDatasetId} options={datasets.map((dataset) => ({ value: dataset.id, label: dataset.name }))} onChange={setSelectedDatasetId} />
          <Select aria-label="选择评估集版本" placeholder="选择版本" value={selectedVersion} options={versions.map((version) => ({ value: version.version, label: `v${version.version} · ${version.cases.length} 题` }))} onChange={setSelectedVersion} />
          <Segmented options={[{ label: '向量', value: 'VECTOR' }, { label: '混合', value: 'HYBRID' }]} value={retrievalStrategy} onChange={(value) => setRetrievalStrategy(value as RagEvaluationRetrievalStrategy)} />
          <Segmented options={[{ label: '不重排', value: 'NONE' }, { label: '词法重排', value: 'LEXICAL' }]} value={rerankStrategy} onChange={(value) => setRerankStrategy(value as RagEvaluationRerankStrategy)} />
          <label className="evaluation-field"><span>TopK</span><InputNumber min={1} max={20} value={topK} onChange={(value) => setTopK(value ?? 5)} /></label>
          <label className="evaluation-field"><span>候选池</span><InputNumber min={1} max={100} value={candidatePoolSize} onChange={(value) => setCandidatePoolSize(value ?? 20)} /></label>
          <label className="evaluation-field"><span>Recall 下限（%）</span><InputNumber min={0} max={100} value={minimumRecall} onChange={(value) => setMinimumRecall(value ?? undefined)} /></label>
          <label className="evaluation-field"><span>NDCG 下限（%）</span><InputNumber min={0} max={100} value={minimumNdcg} onChange={(value) => setMinimumNdcg(value ?? undefined)} /></label>
        </div>
      </section>

      {selectedJob?.qualityGateResult?.status === 'FAILED' ? (
        <Alert type="error" showIcon message="质量门禁未通过" description={selectedJob.qualityGateResult.violations.join('；')} />
      ) : null}

      <section className="panel evaluation-trend-panel">
        <div className="item-line"><h3>指标趋势</h3><span className="muted">成功任务按完成时间排列</span></div>
        {trend?.points.length ? (
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={trend.points} margin={{ top: 8, right: 12, left: 0, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="jobId" tickFormatter={(value) => `#${value}`} />
              <YAxis domain={[0, 100]} unit="%" />
              <ChartTooltip />
              <Legend />
              <Line type="monotone" dataKey="metrics.recallAtK" name="Recall@K" stroke="#147d72" strokeWidth={2} />
              <Line type="monotone" dataKey="metrics.ndcgAtK" name="NDCG@K" stroke="#2563eb" strokeWidth={2} />
              <Line type="monotone" dataKey="metrics.faithfulness" name="忠实度" stroke="#b45309" strokeWidth={2} />
              <Line type="monotone" dataKey="metrics.answerRelevance" name="答案相关性" stroke="#7c3aed" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="完成两次实验后可观察指标趋势" />}
      </section>

      <section className="panel">
        <div className="item-line"><h3>评估任务</h3><span className="muted">成功 {dashboard?.successfulJobCount ?? 0}/{dashboard?.totalJobCount ?? 0}</span></div>
        {dashboard?.recentJobs.length ? (
          <Table columns={jobColumns} dataSource={dashboard.recentJobs} pagination={false} rowKey="id" scroll={{ x: 1240 }} rowClassName={(row) => row.id === selectedJob?.id ? 'evaluation-row-selected' : ''} onRow={(row) => ({ onClick: () => setSelectedJob(row) })} />
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无评估任务" />}
      </section>

      <section className="panel">
        <div className="item-line"><h3>逐题证据</h3>{selectedJob ? <Tag>任务 #{selectedJob.id}</Tag> : null}</div>
        {selectedJob?.caseResults.length ? (
          <Table columns={caseColumns} dataSource={selectedJob.caseResults} pagination={false} rowKey="caseKey" scroll={{ x: 1280 }} />
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择已完成任务查看逐题指标" />}
      </section>

      <section className="panel">
        <div className="item-line">
          <h3>最新版本差异</h3>
          {versionDiff ? <span className="muted">v{versionDiff.fromVersion} → v{versionDiff.toVersion} · 新增 {versionDiff.added} · 修改 {versionDiff.modified} · 删除 {versionDiff.removed}</span> : null}
        </div>
        {versionDiff ? <Table columns={diffColumns} dataSource={versionDiff.cases} pagination={{ pageSize: 10 }} rowKey="caseKey" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="创建第二个评估集版本后可查看逐题差异" />}
      </section>

      <Modal title={modalMode === 'dataset' ? '创建固定评估集' : '创建不可变新版本'} open={modalOpen} okText={modalMode === 'dataset' ? '创建第 1 版' : '创建下一版本'} cancelText="取消" confirmLoading={creating} width={760} onOk={() => void submitDatasetForm()} onCancel={() => setModalOpen(false)}>
        <Form form={form} layout="vertical">
          {modalMode === 'dataset' ? (
            <>
              <Form.Item name="name" label="评估集名称" rules={[{ required: true, message: '请输入评估集名称' }]}><Input maxLength={120} placeholder="例如：Java 并发知识回归集" /></Form.Item>
              <Form.Item name="description" label="用途说明"><Input.TextArea maxLength={1000} rows={2} placeholder="记录评估范围、资料版本和目标" /></Form.Item>
            </>
          ) : (
            <Form.Item name="changeNote" label="版本变更说明"><Input.TextArea maxLength={500} rows={2} placeholder="说明新增、修正或删除了哪些评估题" /></Form.Item>
          )}
          <Form.Item name="casesJson" label="评估题目 JSON" rules={[{ required: true, message: '请输入评估题目' }]}><Input.TextArea className="evaluation-json-input" rows={18} spellCheck={false} /></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
