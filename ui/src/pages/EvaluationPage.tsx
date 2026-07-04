import { BarChart3, Clock3, Coins, Quote } from 'lucide-react';
import { MetricCard } from '../components/MetricCard';
import { SectionHeader } from '../components/SectionHeader';

export function EvaluationPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="评估观测"
        description="第一阶段展示 RAG 评估和 Agent 调用链路的页面结构，后续接入真实指标。"
      />

      <div className="metric-grid">
        <MetricCard label="Recall@5" value="91%" hint="基于固定评估集统计" icon={<BarChart3 size={20} />} />
        <MetricCard label="引用覆盖率" value="86%" hint="回答包含来源的比例" icon={<Quote size={20} />} />
        <MetricCard label="平均耗时" value="2.4s" hint="检索与生成总耗时" icon={<Clock3 size={20} />} />
        <MetricCard label="平均 Token" value="1.8k" hint="单次问答消耗估计" icon={<Coins size={20} />} />
      </div>

      <section className="panel">
        <h3>最近 RAG 调用记录</h3>
        <div className="trace-table">
          <div>问题</div>
          <div>检索策略</div>
          <div>TopK</div>
          <div>耗时</div>
          <div>结果</div>

          <span>线程池参数怎么理解？</span>
          <span>向量检索</span>
          <span>5</span>
          <span>2.1s</span>
          <span>已引用来源</span>

          <span>Spring AI 如何做 Tool Calling？</span>
          <span>混合检索</span>
          <span>8</span>
          <span>2.8s</span>
          <span>等待评估</span>
        </div>
      </section>
    </div>
  );
}
