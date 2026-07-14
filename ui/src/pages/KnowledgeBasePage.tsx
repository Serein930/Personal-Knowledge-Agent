import { Badge, Button, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentDto,
  BackendDocumentSourceType,
  BackendIngestionStatus,
  PageResult,
} from '../api/contracts';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';

const statusView: Record<BackendIngestionStatus, { label: string; color: 'success' | 'processing' | 'default' | 'error' }> = {
  PENDING: { label: '等待中', color: 'default' },
  RUNNING: { label: '处理中', color: 'processing' },
  SUCCEEDED: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  CANCELED: { label: '已取消', color: 'default' },
};

const sourceLabel: Record<BackendDocumentSourceType, string> = {
  PDF: 'PDF',
  MARKDOWN: 'Markdown',
  WEB_PAGE: '网页文章',
  WORD: 'Word',
  TEXT: '文本',
  CODE: '代码',
};

const columns: ColumnsType<BackendDocumentDto> = [
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '来源', dataIndex: 'sourceType', key: 'sourceType', render: (value: BackendDocumentSourceType) => sourceLabel[value] },
  { title: '知识空间', dataIndex: 'workspaceId', key: 'workspaceId' },
  { title: '标签', dataIndex: 'tags', key: 'tags', render: (tags: string[]) => tags.map((tag) => <Tag key={tag}>{tag}</Tag>) },
  {
    title: '摄取状态', dataIndex: 'ingestionStatus', key: 'ingestionStatus',
    render: (status: BackendIngestionStatus) => <Badge status={statusView[status].color} text={statusView[status].label} />,
  },
  { title: '片段数', dataIndex: 'chunkCount', key: 'chunkCount' },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', render: (value: string) => new Date(value).toLocaleString() },
];

export function KnowledgeBasePage() {
  const [documents, setDocuments] = useState<BackendDocumentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const page = await apiClient.get<PageResult<BackendDocumentDto>>(
        `/v1/workspaces/${env.workspaceId}/documents?page=1&pageSize=50`,
      );
      setDocuments(page.records);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '知识库加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadDocuments(); }, [loadDocuments]);

  return (
    <div className="page-stack">
      <SectionHeader
        title="知识库"
        description={`知识空间 ${env.workspaceId}`}
        action={<Button icon={<RefreshCw size={16} />} onClick={loadDocuments}>刷新</Button>}
      />
      <PageState
        loading={loading}
        error={error}
        onRetry={loadDocuments}
        empty={documents.length === 0}
        emptyDescription="暂无知识资产，请先在采集中心提交文件或网页链接。"
      >
        <section className="panel">
          <Table columns={columns} dataSource={documents} pagination={false} rowKey="id" scroll={{ x: 920 }} />
        </section>
      </PageState>
    </div>
  );
}
