import { Button, Progress, Tag } from 'antd';
import { SectionHeader } from '../components/SectionHeader';
import { studyTasks } from '../data/mockData';

export function StudyPlanPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="学习计划"
        description="围绕知识库资料生成学习计划、复习卡片和薄弱点分析。"
        action={<Button type="primary">生成本周计划</Button>}
      />

      <div className="card-grid">
        {studyTasks.map((task) => (
          <section className="panel" key={task.title}>
            <h3>{task.title}</h3>
            <p className="muted">{task.focus}</p>
            <Progress percent={task.progress} />
            <div className="tag-row">
              <Tag>复习卡片</Tag>
              <Tag>薄弱点</Tag>
              <Tag>学习路径</Tag>
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
