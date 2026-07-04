import { Button, Input, Progress, Select, Tag, Upload } from 'antd';
import { Link, UploadCloud } from 'lucide-react';
import type { IngestionTaskDto } from '../api/contracts';
import { SectionHeader } from '../components/SectionHeader';

const recentTasks: IngestionTaskDto[] = [
  {
    id: 'task-001',
    title: 'Spring AI 官方文档摘录',
    source: 'https://docs.spring.io/spring-ai/reference/',
    status: '处理中',
    progress: 62,
    createdAt: '2026-07-04 20:40',
  },
  {
    id: 'task-002',
    title: 'Java 并发编程笔记',
    source: '本地 Markdown 文件',
    status: '已完成',
    progress: 100,
    createdAt: '2026-07-04 20:28',
  },
];

export function IngestionPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="采集中心"
        description="第二阶段补充任务状态雏形，后续接入文件上传、网页采集和异步摄取 API。"
      />

      <div className="two-column">
        <section className="panel">
          <h3>上传学习资料</h3>
          <p className="muted">支持 PDF、Markdown、Word、TXT 和代码文件。真实上传将在后端对象存储接入后启用。</p>
          <Upload.Dragger multiple beforeUpload={() => false} className="upload-zone">
            <UploadCloud size={32} />
            <p>拖拽文件到此处，或点击选择文件</p>
            <span>第二阶段仍不会真正上传文件</span>
          </Upload.Dragger>
        </section>

        <section className="panel">
          <h3>采集网页文章</h3>
          <p className="muted">用于提交 CSDN、掘金、博客园、官方文档等技术文章链接。</p>
          <div className="form-stack">
            <label>
              <span>目标知识空间</span>
              <Select
                defaultValue="java"
                options={[
                  { value: 'java', label: 'Java 后端学习' },
                  { value: 'agent', label: 'Agent 工程化' },
                  { value: 'interview', label: '面试准备' },
                ]}
              />
            </label>
            <label>
              <span>文章链接</span>
              <Input prefix={<Link size={16} />} placeholder="https://blog.csdn.net/..." />
            </label>
            <Button type="primary">创建采集任务</Button>
          </div>
        </section>
      </div>

      <section className="panel">
        <h3>最近采集任务</h3>
        <div className="compact-list">
          {recentTasks.map((task) => (
            <article key={task.id}>
              <div className="item-line">
                <strong>{task.title}</strong>
                <Tag>{task.status}</Tag>
              </div>
              <span>{task.source} · {task.createdAt}</span>
              <Progress percent={task.progress} size="small" />
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
