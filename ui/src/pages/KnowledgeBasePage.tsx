import { Badge, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SectionHeader } from '../components/SectionHeader';
import { knowledgeItems } from '../data/mockData';
import type { KnowledgeItem } from '../types';

const statusColor: Record<KnowledgeItem['status'], 'success' | 'processing' | 'default' | 'error'> = {
  已完成: 'success',
  处理中: 'processing',
  等待中: 'default',
  失败: 'error',
};

const columns: ColumnsType<KnowledgeItem> = [
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
    dataIndex: 'workspace',
    key: 'workspace',
  },
  {
    title: '标签',
    dataIndex: 'tags',
    key: 'tags',
    render: (tags: string[]) => tags.map((tag) => <Tag key={tag}>{tag}</Tag>),
  },
  {
    title: '摄取状态',
    dataIndex: 'status',
    key: 'status',
    render: (status: KnowledgeItem['status']) => <Badge status={statusColor[status]} text={status} />,
  },
  {
    title: 'Chunks',
    dataIndex: 'chunks',
    key: 'chunks',
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    key: 'updatedAt',
  },
];

export function KnowledgeBasePage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="知识库"
        description="管理文档、网页文章和后续向量化后的知识片段。"
      />

      <section className="panel">
        {/* 第一阶段使用静态表格，后续会替换为分页 API 和筛选条件。 */}
        <Table
          columns={columns}
          dataSource={knowledgeItems}
          pagination={false}
          rowKey="id"
          scroll={{ x: 920 }}
        />
      </section>
    </div>
  );
}
