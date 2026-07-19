import { Button, Input, Modal, Popconfirm, Select, Tag, Tooltip, message } from 'antd';
import { Bot, Check, History, Plus, SendHorizontal, Trash2, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import type {
  CreatedToolConfirmationDto,
  BackendDocumentDto,
  ChatConversationDto,
  ChatMessageDto,
  DocumentKeyPointDto,
  DecidedToolConfirmationDto,
  KnowledgeNoteDto,
  PageResult,
  RagCitationDto,
  StudyFlashcardDto,
  ToolCallSummaryDto,
} from '../api/contracts';
import { streamRagChat } from '../api/ragStream';
import { SectionHeader } from '../components/SectionHeader';
import { useAppSession } from '../contexts/AppSessionContext';

interface ChatMessageView {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

const toolLabel: Record<string, string> = {
  'knowledge.search': '知识检索',
  'document.read_chunk': '读取文档片段',
  'note.create': '创建笔记',
  'flashcard.create': '创建复习卡片',
};

export function AgentChatPage() {
  const { workspaceId = 0 } = useAppSession();
  const [question, setQuestion] = useState('');
  const [conversationId, setConversationId] = useState<number>();
  const [messages, setMessages] = useState<ChatMessageView[]>([]);
  const [citations, setCitations] = useState<RagCitationDto[]>([]);
  const [toolCalls, setToolCalls] = useState<ToolCallSummaryDto[]>([]);
  const [pendingProposal, setPendingProposal] = useState<CreatedToolConfirmationDto>();
  const [notes, setNotes] = useState<KnowledgeNoteDto[]>([]);
  const [flashcards, setFlashcards] = useState<StudyFlashcardDto[]>([]);
  const [documents, setDocuments] = useState<BackendDocumentDto[]>([]);
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<number[]>([]);
  const [conversations, setConversations] = useState<ChatConversationDto[]>([]);
  const [keyPoints, setKeyPoints] = useState<Array<DocumentKeyPointDto & { documentId: number }>>([]);
  const [sending, setSending] = useState(false);
  const [deciding, setDeciding] = useState(false);

  const loadKnowledgeOutputs = useCallback(async () => {
    const [notePage, flashcardPage] = await Promise.all([
      apiClient.get<PageResult<KnowledgeNoteDto>>(`/v1/workspaces/${workspaceId}/notes?page=1&pageSize=5`),
      apiClient.get<PageResult<StudyFlashcardDto>>(`/v1/workspaces/${workspaceId}/flashcards?page=1&pageSize=5`),
    ]);
    setNotes(notePage.records);
    setFlashcards(flashcardPage.records);
  }, [workspaceId]);

  const loadChatWorkspace = useCallback(async () => {
    const [documentPage, conversationPage] = await Promise.all([
      apiClient.get<PageResult<BackendDocumentDto>>(
        `/v1/workspaces/${workspaceId}/documents?page=1&pageSize=100&status=SUCCEEDED`,
      ),
      apiClient.get<PageResult<ChatConversationDto>>(
        `/v1/workspaces/${workspaceId}/chat/conversations?page=1&pageSize=50`,
      ),
    ]);
    setDocuments(documentPage.records);
    setConversations(conversationPage.records);
  }, [workspaceId]);

  useEffect(() => {
    void loadKnowledgeOutputs().catch(() => undefined);
    void loadChatWorkspace().catch(() => undefined);
  }, [loadChatWorkspace, loadKnowledgeOutputs]);

  useEffect(() => {
    if (selectedDocumentIds.length === 0) {
      setKeyPoints([]);
      return;
    }
    void Promise.all(selectedDocumentIds.map(async (documentId) => {
      const points = await apiClient.get<DocumentKeyPointDto[]>(
        `/v1/workspaces/${workspaceId}/documents/${documentId}/key-points`,
      );
      return points.map((point) => ({ ...point, documentId }));
    })).then((groups) => setKeyPoints(groups.flat())).catch((error) => {
      message.error(error instanceof Error ? error.message : '核心知识点加载失败');
    });
  }, [selectedDocumentIds, workspaceId]);

  const openConversation = async (nextConversationId: number) => {
    try {
      const page = await apiClient.get<PageResult<ChatMessageDto>>(
        `/v1/workspaces/${workspaceId}/chat/conversations/${nextConversationId}/messages?page=1&pageSize=100`,
      );
      setConversationId(nextConversationId);
      setMessages(page.records.map((item) => ({
        id: `history-${item.id}`,
        role: item.role === 'USER' ? 'user' : 'assistant',
        content: item.content || (item.status === 'FAILED' ? `回答失败：${item.failureReason ?? '未知原因'}` : ''),
      })));
      setCitations([]);
      setToolCalls([]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '历史会话加载失败');
    }
  };

  const startNewConversation = () => {
    setConversationId(undefined);
    setMessages([]);
    setCitations([]);
    setToolCalls([]);
  };

  const deleteCurrentConversation = async () => {
    if (!conversationId) return;
    try {
      await apiClient.delete<void>(
        `/v1/workspaces/${workspaceId}/chat/conversations/${conversationId}`,
      );
      startNewConversation();
      await loadChatWorkspace();
      message.success('历史会话已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会话删除失败');
    }
  };

  const sendQuestion = async () => {
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion || sending) return;
    const assistantViewId = `assistant-${Date.now()}`;
    setMessages((current) => [
      ...current,
      { id: `user-${Date.now()}`, role: 'user', content: normalizedQuestion },
      { id: assistantViewId, role: 'assistant', content: '' },
    ]);
    setQuestion('');
    setCitations([]);
    setToolCalls([]);
    setSending(true);
    try {
      await streamRagChat(workspaceId, normalizedQuestion, conversationId, {
        onMetadata: (event) => setConversationId(event.conversationId),
        onDelta: (event) => setMessages((current) => current.map((item) => (
          item.id === assistantViewId ? { ...item, content: item.content + event.content } : item
        ))),
        onCitation: (event) => setCitations((current) => [...current, event.citation]),
        onToolCall: (event) => setToolCalls((current) => [...current, event.toolCall]),
        onConfirmationRequired: (event) => setPendingProposal(event.proposal),
      }, selectedDocumentIds);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '问答请求失败');
      setMessages((current) => current.map((item) => (
        item.id === assistantViewId && !item.content ? { ...item, content: '回答生成失败，请稍后重试。' } : item
      )));
    } finally {
      setSending(false);
      void loadChatWorkspace();
    }
  };

  const decideProposal = async (action: 'confirm' | 'reject') => {
    if (!pendingProposal) return;
    setDeciding(true);
    try {
      const result = await apiClient.post<DecidedToolConfirmationDto>(
        `/v1/workspaces/${workspaceId}/agent/write-tool-confirmations/`
          + `${pendingProposal.confirmation.id}/${action}`,
        { confirmationToken: pendingProposal.confirmationToken },
      );
      message.success(action === 'confirm' ? '写工具已确认执行' : '已拒绝本次写入');
      setPendingProposal(undefined);
      if (result.confirmation.status === 'SUCCEEDED') await loadKnowledgeOutputs();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '确认操作失败');
    } finally {
      setDeciding(false);
    }
  };

  return (
    <div className="page-stack">
      <SectionHeader title="Agent 问答" description={`知识空间 ${workspaceId}`} />

      <div className="chat-layout">
        <section className="panel chat-panel">
          <div className="chat-scope-toolbar">
            <div className="chat-scope-field">
              <span><History size={15} /> 历史会话</span>
              <Select
                value={conversationId}
                allowClear
                placeholder="新会话"
                options={conversations.map((item) => ({ value: item.id, label: item.title }))}
                onChange={(value) => value ? void openConversation(value) : startNewConversation()}
              />
            </div>
            <Tooltip title="新建会话">
              <Button aria-label="新建会话" icon={<Plus size={16} />} onClick={startNewConversation} />
            </Tooltip>
            <Popconfirm title="删除当前历史会话？" okText="删除" cancelText="取消" disabled={!conversationId} onConfirm={deleteCurrentConversation}>
              <Tooltip title="删除当前会话">
                <Button aria-label="删除当前会话" danger disabled={!conversationId} icon={<Trash2 size={16} />} />
              </Tooltip>
            </Popconfirm>
          </div>

          <div className="chat-document-scope">
            <span>问答资料范围</span>
            <Select
              mode="multiple"
              allowClear
              maxTagCount="responsive"
              value={selectedDocumentIds}
              placeholder="不选择时检索整个知识空间"
              options={documents.map((document) => ({ value: document.id, label: document.title }))}
              onChange={setSelectedDocumentIds}
            />
          </div>

          {keyPoints.length > 0 ? (
            <div className="chat-key-points">
              <strong>已选资料核心知识点</strong>
              <div>{keyPoints.slice(0, 12).map((point) => (
                <Tag key={`${point.documentId}-${point.chunkId}`}>{point.title}</Tag>
              ))}</div>
            </div>
          ) : null}

          {messages.length === 0 ? <p className="muted">输入问题开始知识库问答</p> : null}
          {messages.map((item) => (
            <div key={item.id} className={`chat-message chat-message--${item.role === 'user' ? 'user' : 'agent'}`}>
              <strong>{item.role === 'user' ? '我' : <><Bot size={16} /> AgentMind</>}</strong>
              <p>{item.content || '正在生成...'}</p>
            </div>
          ))}

          <div className="chat-input-row">
            <Input.TextArea
              rows={3}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="例如：根据资料生成一张线程池复习卡片"
              disabled={sending}
            />
            <Button
              type="primary"
              icon={<SendHorizontal size={16} />}
              loading={sending}
              disabled={!question.trim()}
              onClick={sendQuestion}
            >
              发送
            </Button>
          </div>
        </section>

        <aside className="side-panel">
          <section className="panel">
            <h3>引用来源</h3>
            <div className="compact-list">
              {citations.length === 0 ? <span>暂无引用</span> : citations.map((citation) => (
                <article key={`${citation.documentId}-${citation.chunkId}`}>
                  <strong>{citation.documentTitle}</strong>
                  <span>{citation.excerpt}</span>
                  <Tag color="blue">相关度 {citation.score.toFixed(3)}</Tag>
                </article>
              ))}
            </div>
          </section>

          <section className="panel">
            <h3>工具调用</h3>
            <div className="compact-list">
              {toolCalls.length === 0 ? <span>暂无调用</span> : toolCalls.map((toolCall) => (
                <article key={toolCall.id}>
                  <strong>{toolLabel[toolCall.toolName] ?? toolCall.toolName}</strong>
                  <span>{toolCall.responseSummary ?? toolCall.errorMessage ?? '已记录调用结果'}</span>
                  <Tag color={toolCall.status === 'SUCCEEDED' ? 'green' : 'red'}>{toolCall.status}</Tag>
                </article>
              ))}
            </div>
          </section>

          <section className="panel">
            <h3>最近笔记</h3>
            <div className="compact-list">
              {notes.length === 0 ? <span>暂无笔记</span> : notes.map((note) => (
                <article key={note.id}><strong>{note.title}</strong><span>{note.content}</span></article>
              ))}
            </div>
          </section>

          <section className="panel">
            <h3>最近复习卡片</h3>
            <div className="compact-list">
              {flashcards.length === 0 ? <span>暂无卡片</span> : flashcards.map((flashcard) => (
                <article key={flashcard.id}>
                  <strong>{flashcard.question}</strong>
                  <span>{flashcard.answer}</span>
                </article>
              ))}
            </div>
          </section>
        </aside>
      </div>

      <Modal
        title="确认智能体写入"
        open={Boolean(pendingProposal)}
        closable={false}
        maskClosable={false}
        footer={[
          <Button key="reject" icon={<X size={16} />} loading={deciding} onClick={() => decideProposal('reject')}>
            拒绝
          </Button>,
          <Button key="confirm" type="primary" icon={<Check size={16} />} loading={deciding} onClick={() => decideProposal('confirm')}>
            确认执行
          </Button>,
        ]}
      >
        <div className="confirmation-summary">
          <strong>{toolLabel[pendingProposal?.confirmation.toolName ?? ''] ?? pendingProposal?.confirmation.toolName}</strong>
          <p>{pendingProposal?.confirmation.argumentSummary}</p>
          <span>确认单将在 {pendingProposal ? new Date(pendingProposal.confirmation.expiresAt).toLocaleTimeString() : ''} 过期</span>
        </div>
      </Modal>
    </div>
  );
}
