import { Button, Drawer, Empty, Input, Progress, Tabs, Tag, Upload, message } from 'antd';
import {
  CheckCircle2,
  ChevronRight,
  Clock3,
  FileText,
  Globe2,
  Layers3,
  ListTree,
  CircleHelp,
  Link,
  RefreshCw,
  UploadCloud,
  XCircle,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentDto,
  BackendIngestionTaskDto,
  BackendIngestionStatus,
  DocumentChunkDto,
  DocumentCreatedDto,
  DocumentKeyPointDto,
  PageResult,
} from '../api/contracts';
import { ReadableText } from '../components/ReadableText';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';
import { useAppSession } from '../contexts/AppSessionContext';

const statusLabel: Record<BackendIngestionStatus, string> = {
  PENDING: '等待中',
  RUNNING: '处理中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELED: '已取消',
};

const maxUploadSizeBytes = env.maxUploadSizeMb * 1024 * 1024;

interface DocumentInsight {
  documentId: number;
  title: string;
  keyPoints: DocumentKeyPointDto[];
  chunks: DocumentChunkDto[];
}

interface SelectedKnowledgePoint {
  insight: DocumentInsight;
  point: DocumentKeyPointDto;
  chunk?: DocumentChunkDto;
}

function formatFileSize(sizeBytes: number): string {
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MiB`;
}

function formatSource(source: string): string {
  try {
    const url = new URL(source);
    return url.hostname;
  } catch {
    return source.split(/[\\/]/).filter(Boolean).pop() ?? source;
  }
}

function taskStatusIcon(status: BackendIngestionStatus) {
  if (status === 'SUCCEEDED') return <CheckCircle2 size={16} />;
  if (status === 'FAILED') return <XCircle size={16} />;
  return <Clock3 size={16} />;
}

export function IngestionPage() {
  const { workspaceId = 0 } = useAppSession();
  const [selectedFile, setSelectedFile] = useState<File>();
  const [webUrl, setWebUrl] = useState('');
  const [tasks, setTasks] = useState<BackendIngestionTaskDto[]>([]);
  const [insights, setInsights] = useState<Record<number, DocumentInsight>>({});
  const [selectedPoint, setSelectedPoint] = useState<SelectedKnowledgePoint>();
  const [loadingInsights, setLoadingInsights] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [capturing, setCapturing] = useState(false);
  const loadedDocumentIds = useRef(new Set<number>());

  const loadDocumentInsight = useCallback(async (documentId: number, title: string) => {
    if (loadedDocumentIds.current.has(documentId)) return;
    loadedDocumentIds.current.add(documentId);
    try {
      const [keyPoints, chunks] = await Promise.all([
        apiClient.get<DocumentKeyPointDto[]>(
          `/v1/workspaces/${workspaceId}/documents/${documentId}/key-points`,
        ),
        apiClient.get<DocumentChunkDto[]>(
          `/v1/workspaces/${workspaceId}/documents/${documentId}/chunks`,
        ),
      ]);
      setInsights((current) => ({
        ...current,
        [documentId]: { documentId, title, keyPoints, chunks },
      }));
    } catch (error) {
      loadedDocumentIds.current.delete(documentId);
      message.error(error instanceof Error ? error.message : '知识结构加载失败');
    }
  }, [workspaceId]);

  const loadTask = useCallback(async (taskId: number) => {
    const task = await apiClient.get<BackendIngestionTaskDto>(
      `/v1/workspaces/${workspaceId}/ingestion-tasks/${taskId}`,
    );
    setTasks((current) => [task, ...current.filter((item) => item.id !== task.id)]);
    if (task.status === 'SUCCEEDED') {
      await loadDocumentInsight(task.documentId, formatSource(task.source));
    }
  }, [loadDocumentInsight, workspaceId]);

  useEffect(() => {
    loadedDocumentIds.current.clear();
    setInsights({});
    setLoadingInsights(true);
    void apiClient.get<PageResult<BackendDocumentDto>>(
      `/v1/workspaces/${workspaceId}/documents?page=1&pageSize=6&status=SUCCEEDED`,
    ).then(async (page) => {
      await Promise.all(page.records.map((document) => loadDocumentInsight(document.id, document.title)));
    }).catch((error) => {
      message.error(error instanceof Error ? error.message : '最近知识资产加载失败');
    }).finally(() => setLoadingInsights(false));
  }, [loadDocumentInsight, workspaceId]);

  useEffect(() => {
    const activeTasks = tasks.filter((task) => task.status === 'PENDING' || task.status === 'RUNNING');
    if (activeTasks.length === 0) return undefined;
    const timer = window.setInterval(() => {
      void Promise.all(activeTasks.map((task) => loadTask(task.id)));
    }, 1800);
    return () => window.clearInterval(timer);
  }, [loadTask, tasks]);

  const uploadFile = async () => {
    if (!selectedFile) {
      message.warning('请先选择文件');
      return;
    }
    if (selectedFile.size > maxUploadSizeBytes) {
      message.error(
        `文件大小为 ${formatFileSize(selectedFile.size)}，超过当前允许的 ${env.maxUploadSizeMb} MiB`,
      );
      return;
    }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      const created = await apiClient.upload<DocumentCreatedDto>(
        `/v1/workspaces/${workspaceId}/documents/files`,
        formData,
      );
      await loadTask(created.taskId);
      setSelectedFile(undefined);
      message.success('文件摄取任务已创建');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文件上传失败');
    } finally {
      setUploading(false);
    }
  };

  const captureWebPage = async () => {
    if (!webUrl.trim()) {
      message.warning('请输入网页链接');
      return;
    }
    setCapturing(true);
    try {
      const created = await apiClient.post<DocumentCreatedDto>(
        `/v1/workspaces/${workspaceId}/documents/web-pages`,
        { url: webUrl.trim(), tags: [] },
      );
      await loadTask(created.taskId);
      setWebUrl('');
      message.success('网页采集任务已创建');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '网页采集失败');
    } finally {
      setCapturing(false);
    }
  };

  const refreshTasks = async () => {
    await Promise.all(tasks.map((task) => loadTask(task.id)));
  };

  return (
    <div className="page-stack ingestion-page">
      <SectionHeader
        title="采集中心"
        description={`知识空间 ${workspaceId} · 文件与网页统一进入解析、分段和索引流程`}
      />

      <div className="ingestion-workspace">
        <section className="ingestion-command-panel">
          <div className="ingestion-panel-heading">
            <div>
              <span>新建采集</span>
              <strong>导入知识资产</strong>
            </div>
          </div>
          <Tabs
            defaultActiveKey="file"
            items={[
              {
                key: 'file',
                label: <span><FileText size={16} />本地文件</span>,
                children: (
                  <div className="ingestion-source-pane">
                    <Upload.Dragger
                      maxCount={1}
                      fileList={selectedFile ? [{ uid: selectedFile.name, name: selectedFile.name, status: 'done' }] : []}
                      beforeUpload={(file) => {
                        if (file.size > maxUploadSizeBytes) {
                          message.error(
                            `文件大小为 ${formatFileSize(file.size)}，超过当前允许的 ${env.maxUploadSizeMb} MiB`,
                          );
                          setSelectedFile(undefined);
                          return Upload.LIST_IGNORE;
                        }
                        setSelectedFile(file);
                        return false;
                      }}
                      onRemove={() => {
                        setSelectedFile(undefined);
                        return true;
                      }}
                      className="upload-zone"
                    >
                      <span className="ingestion-upload-icon"><UploadCloud size={26} /></span>
                      <p>拖拽文件到此处，或点击选择文件</p>
                      <span>PDF、Markdown、Word、TXT、HTML 和代码文件，最大 {env.maxUploadSizeMb} MiB</span>
                    </Upload.Dragger>
                    <Button
                      type="primary"
                      icon={<UploadCloud size={16} />}
                      loading={uploading}
                      disabled={!selectedFile}
                      onClick={uploadFile}
                    >
                      上传并开始解析
                    </Button>
                  </div>
                ),
              },
              {
                key: 'url',
                label: <span><Globe2 size={16} />网页链接</span>,
                children: (
                  <div className="ingestion-source-pane ingestion-url-pane">
                    <label>
                      <span>文章地址</span>
                      <Input
                        size="large"
                        prefix={<Link size={16} />}
                        value={webUrl}
                        onChange={(event) => setWebUrl(event.target.value)}
                        placeholder="https://example.com/article"
                        onPressEnter={captureWebPage}
                      />
                    </label>
                    <Button
                      type="primary"
                      icon={<Globe2 size={16} />}
                      loading={capturing}
                      onClick={captureWebPage}
                    >
                      采集并提取正文
                    </Button>
                  </div>
                ),
              },
            ]}
          />
        </section>

        <aside className="ingestion-task-panel">
          <div className="ingestion-panel-heading">
            <div>
              <span>运行状态</span>
              <strong>当前任务</strong>
            </div>
            <Button
              aria-label="刷新采集任务"
              icon={<RefreshCw size={15} />}
              disabled={tasks.length === 0}
              onClick={refreshTasks}
            />
          </div>
          {tasks.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次会话暂无任务" />
          ) : (
            <div className="ingestion-task-list">
              {tasks.map((task) => (
                <article key={task.id}>
                  <div className={`ingestion-task-status status-${task.status.toLowerCase()}`}>
                    {taskStatusIcon(task.status)}
                  </div>
                  <div>
                    <strong>{formatSource(task.source)}</strong>
                    <span>任务 #{task.id} · {statusLabel[task.status]}</span>
                    <Progress
                      percent={task.progress}
                      showInfo={false}
                      size="small"
                      status={task.status === 'FAILED' ? 'exception' : undefined}
                    />
                    {task.errorMessage ? <em>{task.errorMessage}</em> : null}
                  </div>
                </article>
              ))}
            </div>
          )}
        </aside>
      </div>

      <section className="ingestion-knowledge-section">
        <div className="knowledge-section-heading">
          <div>
            <span><Layers3 size={17} />自动提取结果</span>
            <h2>文档章节与核心知识点</h2>
          </div>
          <Tag>{Object.keys(insights).length} 份资产</Tag>
        </div>

        {loadingInsights ? (
          <div className="knowledge-loading">正在读取最近知识资产...</div>
        ) : Object.values(insights).length === 0 ? (
          <Empty description="采集完成后将在此展示章节和重点内容" />
        ) : (
          <div className="knowledge-document-list">
            {Object.values(insights).map((insight) => (
              <article key={insight.documentId} className="knowledge-document">
                <header>
                  <span><FileText size={17} /></span>
                  <div>
                    <strong>{insight.title}</strong>
                    <small>{new Set(insight.chunks.map((chunk) => chunk.headingPath).filter(Boolean)).size || 1} 个章节 · {insight.chunks.length} 个语义片段 · {insight.keyPoints.length} 个核心重点</small>
                  </div>
                </header>
                <div className="ingestion-insight-metrics">
                  <div><ListTree size={15} /><span>章节覆盖</span><strong>{Math.min(100, Math.round(insight.keyPoints.length * 100 / Math.max(1, insight.chunks.length)))}%</strong></div>
                  <div><Sparkline value={insight.chunks.length} /><span>平均片段</span><strong>{Math.round(insight.chunks.reduce((total, chunk) => total + chunk.content.length, 0) / Math.max(1, insight.chunks.length))} 字</strong></div>
                  <div><CircleHelp size={15} /><span>可探索问题</span><strong>{insight.keyPoints.length} 个</strong></div>
                </div>
                <div className="knowledge-outline-list">
                  {insight.keyPoints.map((point, index) => {
                    const chunk = insight.chunks.find((item) => item.id === point.chunkId);
                    return (
                      <button
                        type="button"
                        key={point.chunkId}
                        onClick={() => setSelectedPoint({ insight, point, chunk })}
                      >
                        <span className="knowledge-outline-index">{String(index + 1).padStart(2, '0')}</span>
                        <span className="knowledge-outline-content">
                          <strong>{point.title}</strong>
                          <small>{point.summary}</small>
                          <em>建议探索：如何理解“{point.title}”？</em>
                        </span>
                        <ChevronRight size={17} />
                      </button>
                    );
                  })}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <Drawer
        className="knowledge-detail-drawer"
        width={560}
        title="知识点详情"
        open={Boolean(selectedPoint)}
        onClose={() => setSelectedPoint(undefined)}
      >
        {selectedPoint ? (
          <div className="knowledge-detail">
            <span className="knowledge-detail__source">{selectedPoint.insight.title}</span>
            <h2>{selectedPoint.point.title}</h2>
            <div className="knowledge-detail__summary">
              <strong>重点摘要</strong>
              <p>{selectedPoint.point.summary}</p>
            </div>
            <div className="knowledge-detail__question">
              <CircleHelp size={17} />
              <div><strong>建议进一步思考</strong><p>“{selectedPoint.point.title}”解决了什么问题，在实际场景中如何应用？</p></div>
            </div>
            <div className="knowledge-detail__content">
              <div>
                <strong>原文片段</strong>
                {selectedPoint.chunk ? (
                  <span>片段 {selectedPoint.chunk.sequence + 1} · 字符 {selectedPoint.chunk.charStart}-{selectedPoint.chunk.charEnd}</span>
                ) : null}
              </div>
              <ReadableText content={selectedPoint.chunk?.content ?? selectedPoint.point.summary} />
            </div>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}

function Sparkline({ value }: { value: number }) {
  return <span className="mini-sparkline" aria-label={`${value} 个片段`}><i /><i /><i /><i /></span>;
}
