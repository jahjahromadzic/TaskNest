import { TaskDetail } from '../../api/models';

export type TaskStatus = NonNullable<TaskDetail['status']>;

interface StatusStyle {
  label: string;
  badge: string;
}

export const TASK_STATUS: Record<TaskStatus, StatusStyle> = {
  DRAFT: { label: 'Draft', badge: 'bg-slate-100 text-slate-700 border-slate-200' },
  PUBLISHED: { label: 'Open for offers', badge: 'bg-teal-50 text-teal-800 border-teal-200' },
  ASSIGNED: { label: 'Assigned', badge: 'bg-sky-50 text-sky-800 border-sky-200' },
  IN_PROGRESS: { label: 'In progress', badge: 'bg-indigo-50 text-indigo-700 border-indigo-200' },
  COMPLETED: { label: 'Waiting for confirmation', badge: 'bg-amber-50 text-amber-800 border-amber-200' },
  CLOSED: { label: 'Closed', badge: 'bg-green-50 text-green-800 border-green-200' },
  CANCELLED: { label: 'Cancelled', badge: 'bg-slate-100 text-slate-600 border-slate-200' },
  EXPIRED: { label: 'Expired', badge: 'bg-slate-100 text-slate-600 border-slate-200' },
  REMOVED: { label: 'Removed by moderation', badge: 'bg-red-50 text-red-700 border-red-200' },
};

export const LIFECYCLE: TaskStatus[] = ['PUBLISHED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'];

export function isStopped(status: TaskStatus | undefined): boolean {
  return status === 'CANCELLED' || status === 'EXPIRED' || status === 'REMOVED';
}
