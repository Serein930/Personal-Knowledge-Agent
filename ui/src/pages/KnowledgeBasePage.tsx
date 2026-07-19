import { Badge, Button, Drawer, Empty, Input, List, Modal, Popconfirm, Space, Table, Tag, Tooltip, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { BookOpenText, Pencil, RefreshCw, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentDto,
  BackendDocumentSourceType,
  BackendIngestionStatus,
  DocumentKeyPointDto,
  PageResult,
} from '../api/contracts';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { useAppSession } from '../contexts/AppSessionContext';

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

export function KnowledgeBasePage() {
  const { workspaceId = 0 } = useAppSession();
  const [documents, setDocuments] = useState<BackendDocumentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [renaming, setRenaming] = useState<BackendDocumentDto>();
  const [nextTitle, setNextTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [pointDocument, setPointDocument] = useState<BackendDocumentDto>();
  const [keyPoints, setKeyPoints] = useState<DocumentKeyPointDto[]>([]);
  const [pointsLoading, setPointsLoading] = useState(false);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const page = await apiClient.get<PageResult<BackendDocumentDto>>(
        `/v1/workspaces/${workspaceId}/documents?page=1&pageSize=50`,
      );
      setDocuments(page.records);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '知识库加载失败');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => { void loadDocuments(); }, [loadDocuments]);

  const openKeyPoints = async (document: BackendDocumentDto) => {
    setPointDocument(document);
    setPointsLoading(true);
    setKeyPoints([]);
    try {
      setKeyPoints(await apiClient.get<DocumentKeyPointDto[]>(
        `/v1/workspaces/${workspaceId}/documents/${document.id}/key-points`,
      ));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '核心知识点加载失败');
    } finally {
      setPointsLoading(false);
    }
  };

  const saveRename = async () => {
    if (!renaming || !nextTitle.trim()) return;
    setSaving(true);
    try {
      await apiClient.patch<BackendDocumentDto>(
        `/v1/workspaces/${workspaceId}/documents/${renaming.id}`,
        { title: nextTitle.trim() },
      );
      setRenaming(undefined);
      message.success('知识资产已重命名');
      await loadDocuments();
    } catch (saveError) {
      message.error(saveError instanceof Error ? saveError.message : '重命名失败');
    } finally {
      setSaving(false);
    }
  };

  const deleteDocument = async (document: BackendDocumentDto) => {
    try {
      await apiClient.delete<void>(`/v1/workspaces/${workspaceId}/documents/${document.id}`);
      message.success('知识资产已删除');
      await loadDocuments();
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : '删除失败');
    }
  };

  const columns = useMemo<ColumnsType<BackendDocumentDto>>(() => [
    { title: '标题', dataIndex: 'title', key: 'title', width: 240 },
    { title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 110, render: (value: BackendDocumentSourceType) => sourceLabel[value] },
    { title: '标签', dataIndex: 'tags', key: 'tags', render: (tags: string[]) => tags.map((tag) => <Tag key={tag}>{tag}</Tag>) },
    {
      title: '摄取状态', dataIndex: 'ingestionStatus', key: 'ingestionStatus', width: 120,
      render: (status: BackendIngestionStatus) => <Badge status={statusView[status].color} text={statusView[status].label} />,
    },
    { title: '片段数', dataIndex: 'chunkCount', key: 'chunkCount', width: 90 },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180, render: (value: string) => new Date(value).toLocaleString() },
    {
      title: '操作', key: 'actions', width: 140, fixed: 'right',
      render: (_, document) => (
        <Space size={4}>
          <Tooltip title="查看核心知识点">
            <Button aria-label="查看核心知识点" icon={<BookOpenText size={16} />} disabled={document.ingestionStatus !== 'SUCCEEDED'} onClick={() => void openKeyPoints(document)} />
          </Tooltip>
          <Tooltip title="重命名">
            <Button aria-label="重命名" icon={<Pencil size={16} />} onClick={() => { setRenaming(document); setNextTitle(document.title); }} />
          </Tooltip>
          <Popconfirm title="删除该知识资产？" description="文档片段和检索索引会同步删除。" okText="删除" cancelText="取消" onConfirm={() => deleteDocument(document)}>
            <Tooltip title="删除"><Button aria-label="删除" danger icon={<Trash2 size={16} />} /></Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ], [workspaceId]);

  return (
    <div className="page-stack">
      <SectionHeader title="知识库" description={`知识空间 ${workspaceId}`} action={<Button icon={<RefreshCw size={16} />} onClick={loadDocuments}>刷新</Button>} />
      <PageState loading={loading} error={error} onRetry={loadDocuments} empty={documents.length === 0} emptyDescription="暂无知识资产，请先在采集中心提交文件或网页链接。">
        <section className="panel">
          <Table columns={columns} dataSource={documents} pagination={false} rowKey="id" scroll={{ x: 1120 }} />
        </section>
      </PageState>

      <Modal title="重命名知识资产" open={Boolean(renaming)} okText="保存" cancelText="取消" confirmLoading={saving} okButtonProps={{ disabled: !nextTitle.trim() }} onOk={saveRename} onCancel={() => setRenaming(undefined)}>
        <Input value={nextTitle} maxLength={200} showCount autoFocus onChange={(event) => setNextTitle(event.target.value)} onPressEnter={saveRename} />
      </Modal>

      <Drawer title={pointDocument ? `${pointDocument.title} · 核心知识点` : '核心知识点'} open={Boolean(pointDocument)} width={520} loading={pointsLoading} onClose={() => setPointDocument(undefined)}>
        {keyPoints.length === 0 && !pointsLoading ? <Empty description="该知识资产尚未提取出有效知识点" /> : (
          <List dataSource={keyPoints} renderItem={(point) => (
            <List.Item><List.Item.Meta title={`${point.sequence}. ${point.title}`} description={point.summary} /></List.Item>
          )} />
        )}
      </Drawer>
    </div>
  );
}
