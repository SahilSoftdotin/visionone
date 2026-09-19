import type { CSSProperties, ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function Card({
  className,
  children,
  style,
}: {
  className?: string;
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <div className={cn('rounded-lg glass text-card-foreground', className)} style={style}>
      {children}
    </div>
  );
}

export function CardHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border/50 px-5 py-4 sm:px-6">
      <h2 className="text-sm font-semibold tracking-[-0.01em]">{title}</h2>
      {action}
    </div>
  );
}

export function CardBody({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('px-5 py-5 sm:px-6', className)}>{children}</div>;
}
