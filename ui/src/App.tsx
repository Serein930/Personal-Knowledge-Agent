import { Spin } from 'antd';
import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { AppShell } from './components/AppShell';
import { PageErrorBoundary } from './components/PageErrorBoundary';
import { useAppSession } from './contexts/AppSessionContext';
import { LoginPage } from './pages/LoginPage';
import type { PageKey } from './types';

const AgentChatPage = lazy(() => import('./pages/AgentChatPage').then((module) => ({ default: module.AgentChatPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const EvaluationPage = lazy(() => import('./pages/EvaluationPage').then((module) => ({ default: module.EvaluationPage })));
const IngestionPage = lazy(() => import('./pages/IngestionPage').then((module) => ({ default: module.IngestionPage })));
const KnowledgeBasePage = lazy(() => import('./pages/KnowledgeBasePage').then((module) => ({ default: module.KnowledgeBasePage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((module) => ({ default: module.SettingsPage })));
const StudyPlanPage = lazy(() => import('./pages/StudyPlanPage').then((module) => ({ default: module.StudyPlanPage })));

const pageKeys: PageKey[] = ['dashboard', 'knowledge', 'ingestion', 'chat', 'study', 'evaluation', 'settings'];

/** 从地址哈希恢复页面，使刷新、前进和后退不会丢失当前工作区位置。 */
function pageFromLocation(): PageKey {
  const candidate = window.location.hash.replace(/^#\/?/, '') as PageKey;
  return pageKeys.includes(candidate) ? candidate : 'dashboard';
}

export default function App() {
  const { loading, authenticated, workspaceId } = useAppSession();
  const [activePage, setActivePage] = useState<PageKey>(pageFromLocation);

  useEffect(() => {
    const synchronizePage = () => setActivePage(pageFromLocation());
    window.addEventListener('hashchange', synchronizePage);
    return () => window.removeEventListener('hashchange', synchronizePage);
  }, []);

  const navigate = (page: PageKey) => {
    if (page === activePage) return;
    window.location.hash = `/${page}`;
  };

  const pageContent = useMemo(() => {
    switch (activePage) {
      case 'knowledge': return <KnowledgeBasePage />;
      case 'ingestion': return <IngestionPage />;
      case 'chat': return <AgentChatPage />;
      case 'study': return <StudyPlanPage />;
      case 'evaluation': return <EvaluationPage />;
      case 'settings': return <SettingsPage />;
      default: return <DashboardPage />;
    }
  }, [activePage, workspaceId]);

  if (loading) return <div className="app-loading"><Spin size="large" /></div>;
  if (!authenticated) return <LoginPage />;

  return (
    <AppShell activePage={activePage} onPageChange={navigate}>
      <PageErrorBoundary resetKey={`${activePage}-${workspaceId ?? 'none'}`}>
        <Suspense fallback={<div className="app-loading"><Spin size="large" /></div>}>
          {workspaceId ? pageContent : <div className="panel">当前账号没有可用知识空间。</div>}
        </Suspense>
      </PageErrorBoundary>
    </AppShell>
  );
}
