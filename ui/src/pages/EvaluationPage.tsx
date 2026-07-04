import { Fragment } from 'react';
import { BarChart3, Clock3, Coins, Quote } from 'lucide-react';
import { MetricCard } from '../components/MetricCard';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { ragTraces } from '../data/adapters';

export function EvaluationPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="评估观测"
        description="第二阶段使用调用记录 DTO 展示 RAG 评估结构，后续接入真实指标。"
      />

      <div className="metric-grid">
        <MetricCard label="Recall@5" value="91%" hint="基于固定评估集统计" icon={<BarChart3 size={20} />} />
        <MetricCard label="引用覆盖率" value="86%" hint="回答包含来源的比例" icon={<Quote size={20} />} />
        <MetricCard label="平均耗时" value="2.4s" hint="检索与生成总耗时" icon={<Clock3 size={20} />} />
        <MetricCard label="平均 Token" value="1.8k" hint="单次问答消耗估计" icon={<Coins size={20} />} />
      </div>

      <PageState empty={ragTraces.length === 0} emptyDescription="暂无 RAG 调用记录。">
        <section className="panel">
          <h3>最近 RAG 调用记录</h3>
          <div className="trace-table">
            <div>问题</div>
            <div>检索策略</div>
            <div>TopK</div>
            <div>耗时</div>
            <div>结果</div>

            {ragTraces.map((trace) => (
              <Fragment key={trace.id}>
                <span>{trace.question}</span>
                <span>{trace.strategy}</span>
                <span>{trace.topK}</span>
                <span>{(trace.latencyMs / 1000).toFixed(1)}s</span>
                <span>{trace.result}</span>
              </Fragment>
            ))}
          </div>
        </section>
      </PageState>
    </div>
  );
}
