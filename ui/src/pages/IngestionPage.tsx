import { Button, Input, Select, Upload } from 'antd';
import { Link, UploadCloud } from 'lucide-react';
import { SectionHeader } from '../components/SectionHeader';

export function IngestionPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="采集中心"
        description="第一阶段仅设计文件上传和网页链接提交的前端形态，后续接入摄取任务 API。"
      />

      <div className="two-column">
        <section className="panel">
          <h3>上传学习资料</h3>
          <p className="muted">支持 PDF、Markdown、Word、TXT 和代码文件。真实上传将在后端对象存储接入后启用。</p>
          <Upload.Dragger multiple beforeUpload={() => false} className="upload-zone">
            <UploadCloud size={32} />
            <p>拖拽文件到此处，或点击选择文件</p>
            <span>第一阶段不会真正上传文件</span>
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
    </div>
  );
}
