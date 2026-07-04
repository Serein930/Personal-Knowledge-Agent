import { Button, Input, Tag } from 'antd';
import { Bot, SendHorizontal } from 'lucide-react';
import { SectionHeader } from '../components/SectionHeader';
import { chatCitations, toolTraces } from '../data/mockData';

export function AgentChatPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="Agent 问答"
        description="面向个人知识库的 RAG 问答界面，后续接入流式响应和工具调用。"
      />

      <div className="chat-layout">
        <section className="panel chat-panel">
          <div className="chat-message chat-message--user">
            <strong>我</strong>
            <p>线程池核心参数应该怎么理解？结合我的 Java 并发资料说明。</p>
          </div>

          <div className="chat-message chat-message--agent">
            <strong><Bot size={16} /> AgentMind</strong>
            <p>
              线程池核心参数可以从任务进入、排队、扩容和拒绝四个阶段理解。核心线程数决定常驻执行能力，
              阻塞队列承接瞬时峰值，最大线程数提供临时扩容上限，拒绝策略则定义系统过载时的保护方式。
            </p>
          </div>

          <div className="chat-input-row">
            <Input.TextArea rows={3} placeholder="向你的知识库提问，例如：帮我总结 Spring AI 的 Tool Calling 流程" />
            <Button type="primary" icon={<SendHorizontal size={16} />}>发送</Button>
          </div>
        </section>

        <aside className="side-panel">
          <section className="panel">
            <h3>引用来源</h3>
            <div className="compact-list">
              {chatCitations.map((citation) => (
                <article key={citation.title}>
                  <strong>{citation.title}</strong>
                  <span>{citation.excerpt}</span>
                  <Tag color="blue">score {citation.score}</Tag>
                </article>
              ))}
            </div>
          </section>

          <section className="panel">
            <h3>工具调用</h3>
            <div className="compact-list">
              {toolTraces.map((trace) => (
                <article key={trace.name}>
                  <strong>{trace.name}</strong>
                  <span>{trace.detail}</span>
                  <Tag>{trace.status}</Tag>
                </article>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
