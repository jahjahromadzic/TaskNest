import { TaskDetail } from '../../api/models';
import { translated } from '../../i18n/translate';

export type TaskStatus = NonNullable<TaskDetail['status']>;

interface StatusStyle {
  readonly label: string;
  badge: string;
}

export const TASK_STATUS: Record<TaskStatus, StatusStyle> = {
  DRAFT: translated({ badge: 'bg-slate-100 text-slate-700 border-slate-200' }, { label: 'status.DRAFT' }),
  PUBLISHED: translated({ badge: 'bg-teal-50 text-teal-800 border-teal-200' }, { label: 'status.PUBLISHED' }),
  ASSIGNED: translated({ badge: 'bg-sky-50 text-sky-800 border-sky-200' }, { label: 'status.ASSIGNED' }),
  IN_PROGRESS: translated({ badge: 'bg-indigo-50 text-indigo-700 border-indigo-200' }, { label: 'status.IN_PROGRESS' }),
  COMPLETED: translated({ badge: 'bg-amber-50 text-amber-800 border-amber-200' }, { label: 'status.COMPLETED' }),
  CLOSED: translated({ badge: 'bg-green-50 text-green-800 border-green-200' }, { label: 'status.CLOSED' }),
  CANCELLED: translated({ badge: 'bg-slate-100 text-slate-600 border-slate-200' }, { label: 'status.CANCELLED' }),
  EXPIRED: translated({ badge: 'bg-slate-100 text-slate-600 border-slate-200' }, { label: 'status.EXPIRED' }),
  REMOVED: translated({ badge: 'bg-red-50 text-red-700 border-red-200' }, { label: 'status.REMOVED' }),
};

export const LIFECYCLE: TaskStatus[] = ['PUBLISHED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'];

export function isStopped(status: TaskStatus | undefined): boolean {
  return status === 'CANCELLED' || status === 'EXPIRED' || status === 'REMOVED';
}

export type OfferStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';

export const OFFER_STATUS: Record<OfferStatus, StatusStyle> = {
  PENDING: translated({ badge: 'bg-sky-50 text-sky-800 border-sky-200' }, { label: 'offerStatus.PENDING' }),
  ACCEPTED: translated({ badge: 'bg-green-50 text-green-800 border-green-200' }, { label: 'offerStatus.ACCEPTED' }),
  REJECTED: translated({ badge: 'bg-slate-100 text-slate-600 border-slate-200' }, { label: 'offerStatus.REJECTED' }),
  WITHDRAWN: translated({ badge: 'bg-slate-100 text-slate-600 border-slate-200' }, { label: 'offerStatus.WITHDRAWN' }),
};
