import {
  Badge,
  Button,
  Drawer,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BookOpenCheck,
  BookOpenText,
  ChevronRight,
  FileSearch,
  FileText,
  FolderTree,
  Pencil,
  Quote,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  BackendDocumentDto,
  BackendDocumentSourceType,
  BackendIngestionStatus,
  DocumentChunkDto,
  DocumentKeyPointDto,
  PageResult,
} from '../api/contracts';
import { PageState } from '../components/PageState';
import { ReadableText } from '../components/ReadableText';
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

interface DocumentInsight {
  document: BackendDocumentDto;
  keyPoints: DocumentKeyPointDto[];
  chunks: DocumentChunkDto[];
}

export function KnowledgeBasePage() {
  const { workspaceId = 0 } = useAppSession();
  const [documents, setDocuments] = useState<BackendDocumentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [sourceFilter, setSourceFilter] = useState<string>('全部');
  const [renaming, setRenaming] = useState<BackendDocumentDto>();
  const [nextTitle, setNextTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [insight, setInsight] = useState<DocumentInsight>();
  const [insightLoading, setInsightLoading] = useState(false);
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunkDto>();

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const page = await apiClient.get<PageResult<BackendDocumentDto>>(
        `/v1/workspaces/${workspaceId}/documents?page=1&pageSize=100`,
      );
      setDocuments(page.records);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '知识库加载失败');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => { void loadDocuments(); }, [loadDocuments]);

  const filteredDocuments = useMemo(() => documents.filter((document) => {
    const matchesKeyword = !keyword.trim()
      || document.title.toLowerCase().includes(keyword.trim().toLowerCase())
      || document.tags.some((tag) => tag.toLowerCase().includes(keyword.trim().toLowerCase()));
    return matchesKeyword && (sourceFilter === '全部' || sourceLabel[document.sourceType] === sourceFilter);
  }), [documents, keyword, sourceFilter]);

  const summary = useMemo(() => ({
    completed: documents.filter((item) => item.ingestionStatus === 'SUCCEEDED').length,
    chunks: documents.reduce((total, item) => total + item.chunkCount, 0),
    types: new Set(documents.map((item) => item.sourceType)).size,
  }), [documents]);

  const openInsight = async (document: BackendDocumentDto) => {
    setInsightLoading(true);
    setSelectedChunk(undefined);
    try {
      const [keyPoints, chunks] = await Promise.all([
        apiClient.get<DocumentKeyPointDto[]>(`/v1/workspaces/${workspaceId}/documents/${document.id}/key-points`),
        apiClient.get<DocumentChunkDto[]>(`/v1/workspaces/${workspaceId}/documents/${document.id}/chunks`),
      ]);
      setInsight({ document, keyPoints, chunks });
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '知识结构加载失败');
    } finally {
      setInsightLoading(false);
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
    {
      title: '知识资产',
      key: 'asset',
      render: (_, document) => (
        <button className="knowledge-asset-cell" type="button" onClick={() => void openInsight(document)}>
          <span><FileText size={17} /></span>
          <div><strong>{document.title}</strong><small>#{document.id} · {sourceLabel[document.sourceType]}</small></div>
        </button>
      ),
    },
    { title: '标签', dataIndex: 'tags', key: 'tags', render: (tags: string[]) => tags.length ? tags.map((tag) => <Tag key={tag}>{tag}</Tag>) : <span className="muted">未标注</span> },
    {
      title: '知识结构',
      key: 'structure',
      width: 150,
      render: (_, document) => <span className="structure-count"><strong>{document.chunkCount}</strong> 个语义片段</span>,
    },
    {
      title: '状态',
      dataIndex: 'ingestionStatus',
      key: 'ingestionStatus',
      width: 110,
      render: (status: BackendIngestionStatus) => <Badge status={statusView[status].color} text={statusView[status].label} />,
    },
    { title: '最近更新', dataIndex: 'updatedAt', key: 'updatedAt', width: 165, render: (value: string) => new Date(value).toLocaleString() },
    {
      title: '',
      key: 'actions',
      width: 132,
      fixed: 'right',
      render: (_, document) => (
        <Space size={4}>
          <Tooltip title="查看知识结构">
            <Button aria-label="查看知识结构" icon={<BookOpenText size={16} />} disabled={document.ingestionStatus !== 'SUCCEEDED'} onClick={() => void openInsight(document)} />
          </Tooltip>
          <Tooltip title="重命名">
            <Button aria-label="重命名" icon={<Pencil size={16} />} onClick={() => { setRenaming(document); setNextTitle(document.title); }} />
          </Tooltip>
          <Popconfirm title="删除该知识资产？" description="语义片段与检索索引将同步删除。" okText="删除" cancelText="取消" onConfirm={() => deleteDocument(document)}>
            <Tooltip title="删除"><Button aria-label="删除" danger icon={<Trash2 size={16} />} /></Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ], [workspaceId]);

  const chapters = useMemo(() => {
    if (!insight) return [];
    const grouped = new Map<string, DocumentChunkDto[]>();
    insight.chunks.forEach((chunk) => {
      const heading = chunk.headingPath?.trim() || `正文片段 ${chunk.sequence + 1}`;
      grouped.set(heading, [...(grouped.get(heading) ?? []), chunk]);
    });
    return Array.from(grouped.entries());
  }, [insight]);

  return (
    <div className="page-stack knowledge-page">
      <SectionHeader
        title="知识库"
        description="管理知识资产，并从章节、重点问题与原文出处理解资料结构"
        action={<Button icon={<RefreshCw size={16} />} onClick={loadDocuments}>刷新</Button>}
      />

      <section className="knowledge-overview-strip">
        <div><span><BookOpenCheck size={18} /></span><p><strong>{summary.completed}</strong><small>可用知识资产</small></p></div>
        <div><span><FolderTree size={18} /></span><p><strong>{summary.chunks}</strong><small>语义知识片段</small></p></div>
        <div><span><Sparkles size={18} /></span><p><strong>{summary.types}</strong><small>已接入资料类型</small></p></div>
        <div className="knowledge-readiness">
          <p><strong>{documents.length ? Math.round(summary.completed * 100 / documents.length) : 0}%</strong><small>知识索引就绪率</small></p>
          <div><span style={{ width: `${documents.length ? summary.completed * 100 / documents.length : 0}%` }} /></div>
        </div>
      </section>

      <section className="knowledge-catalog">
        <header className="knowledge-catalog__toolbar">
          <Input allowClear prefix={<Search size={15} />} value={keyword} placeholder="搜索标题或标签" onChange={(event) => setKeyword(event.target.value)} />
          <Segmented
            value={sourceFilter}
            options={['全部', 'PDF', '网页文章', 'Markdown', 'Word', '文本']}
            onChange={(value) => setSourceFilter(String(value))}
          />
          <span>{filteredDocuments.length} 项结果</span>
        </header>
        <PageState loading={loading} error={error} onRetry={loadDocuments} empty={filteredDocuments.length === 0} emptyDescription="暂无匹配的知识资产">
          <Table className="knowledge-table" columns={columns} dataSource={filteredDocuments} pagination={{ pageSize: 12, hideOnSinglePage: true }} rowKey="id" scroll={{ x: 1000 }} />
        </PageState>
      </section>

      <Modal title="重命名知识资产" open={Boolean(renaming)} okText="保存" cancelText="取消" confirmLoading={saving} onOk={saveRename} onCancel={() => setRenaming(undefined)}>
        <Input maxLength={200} value={nextTitle} onChange={(event) => setNextTitle(event.target.value)} onPressEnter={saveRename} />
      </Modal>

      <Drawer className="knowledge-insight-drawer" width={760} title={insight?.document.title ?? '知识结构'} loading={insightLoading} open={Boolean(insight) || insightLoading} onClose={() => setInsight(undefined)}>
        {insight ? (
          <div className="knowledge-insight">
            <header className="knowledge-insight__hero">
              <span>{sourceLabel[insight.document.sourceType]}</span>
              <h2>{insight.document.title}</h2>
              <p>{chapters.length} 个章节 · {insight.keyPoints.length} 个重点 · {insight.chunks.length} 个可引用片段</p>
            </header>
            <div className="knowledge-insight__columns">
              <section>
                <div className="insight-section-title"><FolderTree size={16} /><strong>章节导航</strong></div>
                <div className="chapter-tree">
                  {chapters.map(([heading, chunks], index) => (
                    <article key={heading}>
                      <span>{String(index + 1).padStart(2, '0')}</span>
                      <div><strong>{heading}</strong><small>{chunks.length} 个片段</small></div>
                      <Button type="text" icon={<ChevronRight size={16} />} onClick={() => setSelectedChunk(chunks[0])} />
                    </article>
                  ))}
                </div>
              </section>
              <section>
                <div className="insight-section-title"><FileSearch size={16} /><strong>关键问题</strong></div>
                <div className="key-question-list">
                  {insight.keyPoints.map((point) => {
                    const chunk = insight.chunks.find((item) => item.id === point.chunkId);
                    return (
                      <button key={point.chunkId} type="button" onClick={() => setSelectedChunk(chunk)}>
                        <strong>如何理解“{point.title}”？</strong>
                        <span>{point.summary}</span>
                        <small><Quote size={13} /> 出处：片段 {chunk ? chunk.sequence + 1 : point.sequence}</small>
                      </button>
                    );
                  })}
                </div>
              </section>
            </div>
            {selectedChunk ? (
              <section className="source-evidence-panel">
                <header><div><Quote size={16} /><strong>原文出处</strong></div><span>片段 {selectedChunk.sequence + 1} · 字符 {selectedChunk.charStart}-{selectedChunk.charEnd}</span></header>
                <h3>{selectedChunk.headingPath || '正文片段'}</h3>
                <ReadableText content={selectedChunk.content} />
              </section>
            ) : null}
          </div>
        ) : <Empty description="暂无知识结构" />}
      </Drawer>
    </div>
  );
}
