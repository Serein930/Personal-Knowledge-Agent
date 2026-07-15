import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Select, Spin, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { BarChart3, Clock3, Coins, GitCompareArrows, Play, Plus, Quote, RefreshCw, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  PageResult,
  RagEvaluationCaseDto,
  RagEvaluationCaseResultDto,
  RagEvaluationDashboardDto,
  RagEvaluationDatasetDto,
  RagEvaluationDatasetVersionDto,
  RagEvaluationJobDto,
  RagEvaluationJobStatus,
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
  RUNNING: { label: '运行中', color: 'processing' },
  SUCCEEDED: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
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

export function EvaluationPage() {
  const [form] = Form.useForm<DatasetFormValues>();
  const [dashboard, setDashboard] = useState<RagEvaluationDashboardDto>();
  const [datasets, setDatasets] = useState<RagEvaluationDatasetDto[]>([]);
  const [versions, setVersions] = useState<RagEvaluationDatasetVersionDto[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<number>();
  const [selectedVersion, setSelectedVersion] = useState<number>();
  const [selectedJob, setSelectedJob] = useState<RagEvaluationJobDto>();
  const [topK, setTopK] = useState(5);
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
    setSelectedJob((current) => current ?? nextDashboard.latestSuccessfulJob ?? nextDashboard.recentJobs[0]);
    if (!selectedDatasetId && datasetPage.records.length > 0) {
      setSelectedDatasetId(datasetPage.records[0].id);
    }
  }, [selectedDatasetId, workspacePath]);

  const loadVersions = useCallback(async (datasetId: number) => {
    const result = await apiClient.get<RagEvaluationDatasetVersionDto[]>(
      `${workspacePath}/datasets/${datasetId}/versions`,
    );
    setVersions(result);
    setSelectedVersion(result[0]?.version);
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
      return;
    }
    void loadVersions(selectedDatasetId).catch((error) => {
      message.error(error instanceof Error ? error.message : '评估集版本加载失败');
    });
  }, [loadVersions, selectedDatasetId]);

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

  const runEvaluation = async () => {
    if (!selectedDatasetId || !selectedVersion) {
      message.warning('请先选择评估集和版本');
      return;
    }
    setRunning(true);
    try {
      const job = await apiClient.post<RagEvaluationJobDto>(`${workspacePath}/jobs`, {
        datasetId: selectedDatasetId,
        datasetVersion: selectedVersion,
        topK,
      });
      setSelectedJob(job);
      await loadOverview();
      if (job.status === 'FAILED') message.error(job.failureReason || '评估任务执行失败');
      else message.success(`评估任务 ${job.id} 已完成`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评估任务启动失败');
    } finally {
      setRunning(false);
    }
  };

  const jobColumns: ColumnsType<RagEvaluationJobDto> = useMemo(() => [
    { title: '任务', dataIndex: 'id', width: 80, render: (value: number) => `#${value}` },
    { title: '评估集版本', key: 'version', render: (_, row) => `${row.datasetId} / v${row.datasetVersion}` },
    { title: '状态', dataIndex: 'status', render: (status: RagEvaluationJobStatus) => <Tag color={statusView[status].color}>{statusView[status].label}</Tag> },
    { title: 'TopK', dataIndex: 'topK', width: 72 },
    { title: '模型', dataIndex: 'modelName' },
    { title: 'Recall@K', key: 'recall', render: (_, row) => row.metrics ? percentage(row.metrics.recallAtK) : '-' },
    { title: '平均耗时', key: 'latency', render: (_, row) => row.metrics ? `${row.metrics.averageLatencyMillis} ms` : '-' },
    { title: '运行时间', dataIndex: 'createdAt', render: (value: string) => new Date(value).toLocaleString() },
  ], []);

  const caseColumns: ColumnsType<RagEvaluationCaseResultDto> = [
    { title: '题目标识', dataIndex: 'caseKey' },
    { title: 'Recall@K', dataIndex: 'recallAtK', render: percentage },
    { title: '首个相关排名', dataIndex: 'firstRelevantRank', render: (value?: number) => value ?? '-' },
    { title: '引用', dataIndex: 'citationCovered', render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '覆盖' : '未覆盖'}</Tag> },
    { title: '拒答判断', dataIndex: 'refusalCorrect', render: (value: boolean) => <Tag color={value ? 'success' : 'error'}>{value ? '正确' : '错误'}</Tag> },
    { title: '耗时', dataIndex: 'elapsedMillis', render: (value: number) => `${value} ms` },
    { title: 'Token', key: 'tokens', render: (_, row) => row.promptTokens + row.completionTokens },
  ];

  if (loading) return <div className="study-loading"><Spin size="large" /></div>;

  return (
    <div className="page-stack">
      <SectionHeader
        title="RAG 评估工作台"
        description={`知识空间 ${env.workspaceId} · 固定评估集、指标基线与逐题证据`}
        action={(
          <div className="evaluation-header-actions">
            <Button icon={<RefreshCw size={16} />} onClick={loadOverview}>刷新</Button>
            <Button icon={<Plus size={16} />} disabled={!selectedDatasetId} onClick={openVersionModal}>创建新版本</Button>
            <Button type="primary" icon={<Plus size={16} />} onClick={openDatasetModal}>新建评估集</Button>
          </div>
        )}
      />

      <div className="evaluation-metric-grid">
        <MetricCard label="Recall@K" value={percentage(latestMetrics?.recallAtK)} hint={signed(delta?.recallAtK, ' 个百分点')} icon={<BarChart3 size={20} />} />
        <MetricCard label="MRR" value={(latestMetrics?.meanReciprocalRank ?? 0).toFixed(4)} hint={signed(delta?.meanReciprocalRank)} icon={<GitCompareArrows size={20} />} />
        <MetricCard label="引用覆盖率" value={percentage(latestMetrics?.citationCoverage)} hint={signed(delta?.citationCoverage, ' 个百分点')} icon={<Quote size={20} />} />
        <MetricCard label="拒答准确率" value={percentage(latestMetrics?.refusalAccuracy)} hint={signed(delta?.refusalAccuracy, ' 个百分点')} icon={<ShieldCheck size={20} />} />
        <MetricCard label="平均耗时" value={`${latestMetrics?.averageLatencyMillis ?? 0} ms`} hint={signed(delta?.averageLatencyMillis, ' ms')} icon={<Clock3 size={20} />} />
        <MetricCard label="Token / 成本" value={String(latestMetrics?.totalTokens ?? 0)} hint={`$${(latestMetrics?.estimatedCostUsd ?? 0).toFixed(6)}${latestMetrics?.tokenUsageEstimated ? ' · 估算' : ''}`} icon={<Coins size={20} />} />
      </div>

      <section className="panel evaluation-run-panel">
        <div>
          <h3>运行固定评估</h3>
          <p className="muted">每次运行冻结评估集版本、检索参数、提示词版本和模型名称。</p>
        </div>
        <Select
          aria-label="选择评估集"
          placeholder="选择评估集"
          value={selectedDatasetId}
          options={datasets.map((dataset) => ({ value: dataset.id, label: dataset.name }))}
          onChange={setSelectedDatasetId}
        />
        <Select
          aria-label="选择评估集版本"
          placeholder="选择版本"
          value={selectedVersion}
          options={versions.map((version) => ({ value: version.version, label: `v${version.version} · ${version.cases.length} 题` }))}
          onChange={setSelectedVersion}
        />
        <InputNumber min={1} max={20} value={topK} onChange={(value) => setTopK(value ?? 5)} addonBefore="TopK" />
        <Button type="primary" icon={<Play size={16} />} loading={running} disabled={datasets.length === 0} onClick={runEvaluation}>开始评估</Button>
      </section>

      {dashboard?.latestComparison && !dashboard.latestComparison.comparable ? (
        <Alert type="info" showIcon message={dashboard.latestComparison.message} />
      ) : null}

      <section className="panel">
        <div className="item-line">
          <h3>评估任务</h3>
          <span className="muted">评估集 {dashboard?.datasetCount ?? 0} 个 · 成功 {dashboard?.successfulJobCount ?? 0}/{dashboard?.totalJobCount ?? 0}</span>
        </div>
        {dashboard?.recentJobs.length ? (
          <Table
            columns={jobColumns}
            dataSource={dashboard.recentJobs}
            pagination={false}
            rowKey="id"
            scroll={{ x: 980 }}
            rowClassName={(row) => row.id === selectedJob?.id ? 'evaluation-row-selected' : ''}
            onRow={(row) => ({ onClick: () => setSelectedJob(row) })}
          />
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无评估任务" />}
      </section>

      <section className="panel">
        <div className="item-line">
          <h3>逐题证据</h3>
          {selectedJob ? <Tag>任务 #{selectedJob.id}</Tag> : null}
        </div>
        {selectedJob?.caseResults.length ? (
          <Table columns={caseColumns} dataSource={selectedJob.caseResults} pagination={false} rowKey="caseKey" scroll={{ x: 820 }} />
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择已完成任务查看逐题指标" />}
      </section>

      <Modal
        title={modalMode === 'dataset' ? '创建固定评估集' : '创建不可变新版本'}
        open={modalOpen}
        okText={modalMode === 'dataset' ? '创建第 1 版' : '创建下一版本'}
        cancelText="取消"
        confirmLoading={creating}
        width={760}
        onOk={submitDatasetForm}
        onCancel={() => setModalOpen(false)}
      >
        <Form form={form} layout="vertical">
          {modalMode === 'dataset' ? (
            <>
              <Form.Item name="name" label="评估集名称" rules={[{ required: true, message: '请输入评估集名称' }]}>
                <Input maxLength={120} placeholder="例如：Java 并发知识回归集" />
              </Form.Item>
              <Form.Item name="description" label="用途说明">
                <Input.TextArea maxLength={1000} rows={2} placeholder="记录评估范围、资料版本和目标" />
              </Form.Item>
            </>
          ) : (
            <Form.Item name="changeNote" label="版本变更说明">
              <Input.TextArea maxLength={500} rows={2} placeholder="说明新增、修正或删除了哪些评估题" />
            </Form.Item>
          )}
          <Form.Item name="casesJson" label="评估题目 JSON" rules={[{ required: true, message: '请输入评估题目' }]}>
            <Input.TextArea className="evaluation-json-input" rows={18} spellCheck={false} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
