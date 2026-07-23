import { Button, Input, Modal, Popconfirm, Select, Tabs, Tag, Tooltip, message } from 'antd';
import {
  BookOpen,
  Bot,
  Check,
  FileText,
  History,
  NotebookPen,
  Plus,
  SendHorizontal,
  Sparkles,
  Trash2,
  Wrench,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
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
import { ReadableText } from '../components/ReadableText';
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

const promptSuggestions = [
  '总结所选资料的三个核心知识点',
  '解释资料中最容易混淆的两个概念',
  '根据资料给我一道面试题并讲解答案',
];

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
  const threadRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => {
    const thread = threadRef.current;
    if (thread) {
      thread.scrollTo({ top: thread.scrollHeight, behavior: sending ? 'smooth' : 'auto' });
    }
  }, [messages, sending]);

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
    <div className="agent-chat-page">
      <div className="agent-chat-shell">
        <aside className="chat-history-rail">
          <Button
            className="chat-new-button"
            icon={<Plus size={16} />}
            onClick={startNewConversation}
          >
            新对话
          </Button>
          <div className="chat-history-rail__title">
            <History size={15} />
            <span>历史会话</span>
          </div>
          <div className="chat-conversation-list">
            {conversations.length === 0 ? (
              <span className="chat-empty-label">暂无历史会话</span>
            ) : conversations.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`chat-conversation-item${conversationId === item.id ? ' is-active' : ''}`}
                onClick={() => void openConversation(item.id)}
              >
                <span>{item.title}</span>
                <time>{new Date(item.updatedAt).toLocaleDateString()}</time>
              </button>
            ))}
          </div>
          <Popconfirm
            title="删除当前历史会话？"
            okText="删除"
            cancelText="取消"
            disabled={!conversationId}
            onConfirm={deleteCurrentConversation}
          >
            <Button
              className="chat-delete-button"
              danger
              disabled={!conversationId}
              icon={<Trash2 size={15} />}
            >
              删除当前会话
            </Button>
          </Popconfirm>
        </aside>

        <main className="chat-conversation-stage">
          <header className="chat-conversation-header">
            <div className="chat-agent-identity">
              <span className="chat-agent-avatar"><Bot size={18} /></span>
              <div>
                <strong>AgentMind</strong>
                <span>知识空间 {workspaceId}</span>
              </div>
            </div>
            <Select
              className="chat-mobile-history"
              value={conversationId}
              allowClear
              placeholder="新会话"
              options={conversations.map((item) => ({ value: item.id, label: item.title }))}
              onChange={(value) => value ? void openConversation(value) : startNewConversation()}
            />
          </header>

          <div className="chat-resource-scope">
            <BookOpen size={16} />
            <Select
              mode="multiple"
              allowClear
              maxTagCount="responsive"
              value={selectedDocumentIds}
              placeholder="检索整个知识空间"
              options={documents.map((document) => ({ value: document.id, label: document.title }))}
              onChange={setSelectedDocumentIds}
              aria-label="问答资料范围"
            />
          </div>

          {keyPoints.length > 0 ? (
            <div className="chat-key-point-strip">
              <Sparkles size={15} />
              <div>{keyPoints.slice(0, 8).map((point) => (
                <Tag key={`${point.documentId}-${point.chunkId}`}>{point.title}</Tag>
              ))}</div>
            </div>
          ) : null}

          <div ref={threadRef} className="chat-thread">
            {messages.length === 0 ? (
              <div className="chat-welcome">
                <span><Sparkles size={24} /></span>
                <h2>今天想从资料中弄懂什么？</h2>
                <div className="chat-suggestions">
                  {promptSuggestions.map((suggestion) => (
                    <button key={suggestion} type="button" onClick={() => setQuestion(suggestion)}>
                      {suggestion}
                    </button>
                  ))}
                </div>
              </div>
            ) : messages.map((item) => (
              <article
                key={item.id}
                className={`chat-turn chat-turn--${item.role === 'user' ? 'user' : 'assistant'}`}
              >
                {item.role === 'assistant' ? (
                  <span className="chat-turn__avatar"><Bot size={17} /></span>
                ) : null}
                <div className="chat-turn__content">
                  {item.role === 'assistant' ? <strong>AgentMind</strong> : null}
                  <ReadableText
                    className="chat-message__content readable-text"
                    content={item.content || '正在生成...'}
                  />
                </div>
              </article>
            ))}
          </div>

          <footer className="chat-composer-area">
            <div className="chat-composer">
              <Input.TextArea
                autoSize={{ minRows: 1, maxRows: 6 }}
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onPressEnter={(event) => {
                  if (!event.shiftKey) {
                    event.preventDefault();
                    void sendQuestion();
                  }
                }}
                placeholder="向你的知识库提问"
                disabled={sending}
                variant="borderless"
              />
              <Tooltip title="发送">
                <Button
                  type="primary"
                  shape="circle"
                  aria-label="发送问题"
                  icon={<SendHorizontal size={17} />}
                  loading={sending}
                  disabled={!question.trim()}
                  onClick={sendQuestion}
                />
              </Tooltip>
            </div>
            <span>
              {selectedDocumentIds.length > 0
                ? `仅检索已选的 ${selectedDocumentIds.length} 份资料`
                : '检索当前知识空间'}
            </span>
          </footer>
        </main>

        <aside className="chat-context-inspector">
          <Tabs
            defaultActiveKey="citations"
            items={[
              {
                key: 'citations',
                label: <span><FileText size={15} />引用</span>,
                children: (
                  <div className="chat-inspector-list">
                    {citations.length === 0 ? <span className="chat-empty-label">暂无引用</span> : citations.map((citation) => (
                      <article key={`${citation.documentId}-${citation.chunkId}`}>
                        <strong>{citation.documentTitle}</strong>
                        <ReadableText content={citation.excerpt} />
                        <Tag color="blue">相关度 {citation.score.toFixed(3)}</Tag>
                      </article>
                    ))}
                  </div>
                ),
              },
              {
                key: 'tools',
                label: <span><Wrench size={15} />工具</span>,
                children: (
                  <div className="chat-inspector-list">
                    {toolCalls.length === 0 ? <span className="chat-empty-label">暂无调用</span> : toolCalls.map((toolCall) => (
                      <article key={toolCall.id}>
                        <strong>{toolLabel[toolCall.toolName] ?? toolCall.toolName}</strong>
                        <span>{toolCall.responseSummary ?? toolCall.errorMessage ?? '已记录调用结果'}</span>
                        <Tag color={toolCall.status === 'SUCCEEDED' ? 'green' : 'red'}>{toolCall.status}</Tag>
                      </article>
                    ))}
                  </div>
                ),
              },
              {
                key: 'assets',
                label: <span><NotebookPen size={15} />资产</span>,
                children: (
                  <div className="chat-inspector-assets">
                    <h3>最近笔记</h3>
                    <div className="chat-inspector-list">
                      {notes.length === 0 ? <span className="chat-empty-label">暂无笔记</span> : notes.map((note) => (
                        <article key={note.id}><strong>{note.title}</strong><span>{note.content}</span></article>
                      ))}
                    </div>
                    <h3>最近复习卡片</h3>
                    <div className="chat-inspector-list">
                      {flashcards.length === 0 ? <span className="chat-empty-label">暂无卡片</span> : flashcards.map((flashcard) => (
                        <article key={flashcard.id}>
                          <strong>{flashcard.question}</strong>
                          <span>{flashcard.answer}</span>
                        </article>
                      ))}
                    </div>
                  </div>
                ),
              },
            ]}
          />
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
