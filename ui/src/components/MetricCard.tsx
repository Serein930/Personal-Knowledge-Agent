import type { ReactNode } from 'react';

interface MetricCardProps {
  label: string;
  value: string;
  hint: string;
  icon: ReactNode;
}

export function MetricCard({ label, value, hint, icon }: MetricCardProps) {
  return (
    <section className="metric-card">
      <div className="metric-card__icon">{icon}</div>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        <p>{hint}</p>
      </div>
    </section>
  );
}
