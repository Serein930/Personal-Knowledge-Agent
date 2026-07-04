import { useMemo, useState } from 'react';
import { AppShell } from './components/AppShell';
import { AgentChatPage } from './pages/AgentChatPage';
import { DashboardPage } from './pages/DashboardPage';
import { EvaluationPage } from './pages/EvaluationPage';
import { IngestionPage } from './pages/IngestionPage';
import { KnowledgeBasePage } from './pages/KnowledgeBasePage';
import { SettingsPage } from './pages/SettingsPage';
import { StudyPlanPage } from './pages/StudyPlanPage';
import type { PageKey } from './types';

export default function App() {
  const [activePage, setActivePage] = useState<PageKey>('dashboard');

  // 暂不引入路由库，先用页面状态支撑前端早期阶段的快速迭代。
  const pageContent = useMemo(() => {
    switch (activePage) {
      case 'knowledge':
        return <KnowledgeBasePage />;
      case 'ingestion':
        return <IngestionPage />;
      case 'chat':
        return <AgentChatPage />;
      case 'study':
        return <StudyPlanPage />;
      case 'evaluation':
        return <EvaluationPage />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <DashboardPage />;
    }
  }, [activePage]);

  return (
    <AppShell activePage={activePage} onPageChange={setActivePage}>
      {pageContent}
    </AppShell>
  );
}
