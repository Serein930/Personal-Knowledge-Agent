import { BrainCircuit, Clock3, FileText, Target } from 'lucide-react';
import { MetricCard } from '../components/MetricCard';
import { SectionHeader } from '../components/SectionHeader';
import { knowledgeItems, studyTasks } from '../data/mockData';

export function DashboardPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="工作台"
        description="汇总知识资产、摄取进度、学习计划和 Agent 问答状态。"
      />

      <div className="metric-grid">
        <MetricCard label="知识资产" value="126" hint="文档、网页和代码片段" icon={<FileText size={20} />} />
        <MetricCard label="今日摄取" value="8" hint="等待向量化 2 项" icon={<Clock3 size={20} />} />
        <MetricCard label="学习计划" value="3" hint="本周仍需复习 17 张卡片" icon={<Target size={20} />} />
        <MetricCard label="Agent 调用" value="42" hint="平均响应 2.4 秒" icon={<BrainCircuit size={20} />} />
      </div>

      <div className="two-column">
        <section className="panel">
          <h3>最近知识资产</h3>
          <div className="compact-list">
            {knowledgeItems.map((item) => (
              <article key={item.id}>
                <strong>{item.title}</strong>
                <span>{item.sourceType} · {item.workspace} · {item.status}</span>
              </article>
            ))}
          </div>
        </section>

        <section className="panel">
          <h3>学习进度</h3>
          <div className="compact-list">
            {studyTasks.map((task) => (
              <article key={task.title}>
                <strong>{task.title}</strong>
                <span>{task.focus}</span>
                <div className="progress-track">
                  <div style={{ width: `${task.progress}%` }} />
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
