import { Badge, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { KnowledgeDocumentDto } from '../api/contracts';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { toKnowledgeDocumentDto } from '../data/adapters';
import { knowledgeItems } from '../data/mockData';
import type { IngestionStatus } from '../types';

const statusColor: Record<IngestionStatus, 'success' | 'processing' | 'default' | 'error'> = {
  已完成: 'success',
  处理中: 'processing',
  等待中: 'default',
  失败: 'error',
};

const columns: ColumnsType<KnowledgeDocumentDto> = [
  {
    title: '标题',
    dataIndex: 'title',
    key: 'title',
  },
  {
    title: '来源',
    dataIndex: 'sourceType',
    key: 'sourceType',
  },
  {
    title: '知识空间',
    dataIndex: 'workspaceName',
    key: 'workspaceName',
  },
  {
    title: '标签',
    dataIndex: 'tags',
    key: 'tags',
    render: (tags: string[]) => tags.map((tag) => <Tag key={tag}>{tag}</Tag>),
  },
  {
    title: '摄取状态',
    dataIndex: 'ingestionStatus',
    key: 'ingestionStatus',
    render: (status: IngestionStatus) => <Badge status={statusColor[status]} text={status} />,
  },
  {
    title: 'Chunks',
    dataIndex: 'chunkCount',
    key: 'chunkCount',
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    key: 'updatedAt',
  },
];

export function KnowledgeBasePage() {
  // 第二阶段使用 DTO 形态的 mock 数据，后续替换为 apiClient.get<PageResult<KnowledgeDocumentDto>>()。
  const documents = knowledgeItems.map(toKnowledgeDocumentDto);

  return (
    <div className="page-stack">
      <SectionHeader
        title="知识库"
        description="管理文档、网页文章和后续向量化后的知识片段。"
      />

      <PageState empty={documents.length === 0} emptyDescription="暂无知识资产，请先在采集中心提交文件或网页链接。">
        <section className="panel">
          <Table
            columns={columns}
            dataSource={documents}
            pagination={false}
            rowKey="id"
            scroll={{ x: 920 }}
          />
        </section>
      </PageState>
    </div>
  );
}
