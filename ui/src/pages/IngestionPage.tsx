import { Button, Input, Progress, Select, Tag, Upload, message } from 'antd';
import { Link, RefreshCw, UploadCloud } from 'lucide-react';
import { useState } from 'react';
import { apiClient } from '../api/client';
import type { BackendIngestionTaskDto, BackendIngestionStatus, DocumentCreatedDto } from '../api/contracts';
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

function formatFileSize(sizeBytes: number): string {
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MiB`;
}

export function IngestionPage() {
  const { workspaceId = 0 } = useAppSession();
  const [selectedFile, setSelectedFile] = useState<File>();
  const [webUrl, setWebUrl] = useState('');
  const [tasks, setTasks] = useState<BackendIngestionTaskDto[]>([]);
  const [uploading, setUploading] = useState(false);
  const [capturing, setCapturing] = useState(false);

  const loadTask = async (taskId: number) => {
    const task = await apiClient.get<BackendIngestionTaskDto>(
      `/v1/workspaces/${workspaceId}/ingestion-tasks/${taskId}`,
    );
    setTasks((current) => [task, ...current.filter((item) => item.id !== task.id)]);
  };

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
    <div className="page-stack">
      <SectionHeader title="采集中心" description={`当前知识空间：${workspaceId}`} />

      <div className="two-column">
        <section className="panel ingestion-upload-panel">
          <h3>上传学习资料</h3>
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
            <UploadCloud size={32} />
            <p>拖拽文件到此处，或点击选择文件</p>
            <span>支持 PDF、Markdown、Word、TXT、HTML 和代码文件，单个文件不超过 {env.maxUploadSizeMb} MiB</span>
          </Upload.Dragger>
          <Button type="primary" loading={uploading} disabled={!selectedFile} onClick={uploadFile}>
            提交文件
          </Button>
        </section>

        <section className="panel">
          <h3>采集网页文章</h3>
          <div className="form-stack">
            <label>
              <span>目标知识空间</span>
              <Select value={workspaceId} options={[{ value: workspaceId, label: `知识空间 ${workspaceId}` }]} />
            </label>
            <label>
              <span>文章链接</span>
              <Input
                prefix={<Link size={16} />}
                value={webUrl}
                onChange={(event) => setWebUrl(event.target.value)}
                placeholder="https://blog.csdn.net/..."
                onPressEnter={captureWebPage}
              />
            </label>
            <Button type="primary" loading={capturing} onClick={captureWebPage}>创建采集任务</Button>
          </div>
        </section>
      </div>

      <section className="panel">
        <div className="item-line">
          <h3>本次联调任务</h3>
          <Button icon={<RefreshCw size={16} />} disabled={tasks.length === 0} onClick={refreshTasks}>
            刷新
          </Button>
        </div>
        {tasks.length === 0 ? <p className="muted">尚未提交任务</p> : (
          <div className="compact-list">
            {tasks.map((task) => (
              <article key={task.id}>
                <div className="item-line">
                  <strong>{task.source}</strong>
                  <Tag>{statusLabel[task.status]}</Tag>
                </div>
                <span>任务 {task.id} · 文档 {task.documentId}</span>
                <Progress percent={task.progress} size="small" status={task.status === 'FAILED' ? 'exception' : undefined} />
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
