import { Button, Select, Tooltip } from 'antd';
import {
  BarChart3,
  BookOpenText,
  BrainCircuit,
  Database,
  FileUp,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Settings,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useAppSession } from '../contexts/AppSessionContext';
import type { PageKey } from '../types';

interface AppShellProps {
  activePage: PageKey;
  onPageChange: (page: PageKey) => void;
  children: ReactNode;
}

const navItems: Array<{ key: PageKey; label: string; icon: ReactNode }> = [
  { key: 'dashboard', label: '工作台', icon: <LayoutDashboard size={18} /> },
  { key: 'knowledge', label: '知识库', icon: <Database size={18} /> },
  { key: 'ingestion', label: '采集中心', icon: <FileUp size={18} /> },
  { key: 'chat', label: 'Agent 问答', icon: <BrainCircuit size={18} /> },
  { key: 'study', label: '学习计划', icon: <GraduationCap size={18} /> },
  { key: 'evaluation', label: '评估观测', icon: <BarChart3 size={18} /> },
  { key: 'settings', label: '设置', icon: <Settings size={18} /> },
];

export function AppShell({ activePage, onPageChange, children }: AppShellProps) {
  const { logout, selectWorkspace, user, workspaceId, workspaces } = useAppSession();
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <BookOpenText size={24} />
          <div><strong>AgentMind</strong><span>个人知识 Agent</span></div>
        </div>
        <nav className="nav-list" aria-label="主导航">
          {navItems.map((item) => (
            <button
              key={item.key}
              className={item.key === activePage ? 'nav-item nav-item--active' : 'nav-item'}
              onClick={() => onPageChange(item.key)}
              type="button"
            >
              {item.icon}<span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div>
            <span className="topbar__eyebrow">Personal Knowledge Agent</span>
            <h1>面向个人学习资料的智能知识中枢</h1>
          </div>
          <div className="topbar__status">
            <Select
              aria-label="当前知识空间"
              value={workspaceId}
              options={workspaces.map((workspace) => ({ value: workspace.id, label: workspace.name }))}
              onChange={selectWorkspace}
            />
            <div><span>{user?.displayName ?? '开发用户'}</span><strong>{user?.role ?? 'USER'}</strong></div>
            <Tooltip title="退出登录">
              <Button aria-label="退出登录" icon={<LogOut size={16} />} onClick={logout} />
            </Tooltip>
          </div>
        </header>
        <div className="page-container">{children}</div>
      </main>
    </div>
  );
}
