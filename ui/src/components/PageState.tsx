import { Alert, Button, Empty, Skeleton } from 'antd';
import type { ReactNode } from 'react';

interface PageStateProps {
  loading?: boolean;
  error?: string;
  empty?: boolean;
  emptyDescription?: string;
  onRetry?: () => void;
  children: ReactNode;
}

export function PageState({
  loading = false,
  error,
  empty = false,
  emptyDescription = '暂无数据',
  onRetry,
  children,
}: PageStateProps) {
  if (loading) {
    return (
      <section className="panel">
        <Skeleton active paragraph={{ rows: 5 }} />
      </section>
    );
  }

  if (error) {
    return (
      <Alert
        action={onRetry ? <Button onClick={onRetry}>重试</Button> : undefined}
        message="数据加载失败"
        description={error}
        type="error"
        showIcon
      />
    );
  }

  if (empty) {
    return (
      <section className="panel">
        <Empty description={emptyDescription} />
      </section>
    );
  }

  return <>{children}</>;
}
