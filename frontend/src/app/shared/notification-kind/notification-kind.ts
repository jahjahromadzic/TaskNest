import type { IconNode } from 'lucide';
import {
  Archive,
  BadgeCheck,
  CircleCheck,
  Hammer,
  Hourglass,
  MapPin,
  MessageSquare,
  PencilLine,
  RotateCcw,
  ShieldAlert,
  Star,
  Tag,
  TimerOff,
  Undo2,
  UserMinus,
} from 'lucide';
import { AppNotification, NotificationType } from '../../api/models';
import { t, translated } from '../../i18n/translate';

export interface NotificationKind {
  readonly title: string;
  icon: IconNode;
  tone: string;
}

const BRAND = 'bg-brand-50 text-brand';
const INFO = 'bg-sky-50 text-sky-600';
const SUCCESS = 'bg-emerald-50 text-emerald-600';
const WARNING = 'bg-amber-50 text-amber-600';
const DANGER = 'bg-red-50 text-red-600';
const NEUTRAL = 'bg-slate-100 text-slate-500';

export const NOTIFICATION_KINDS: Record<NotificationType, NotificationKind> = {
  NEW_TASK_IN_AREA: translated({ icon: MapPin, tone: BRAND }, { title: 'notifications.kinds.NEW_TASK_IN_AREA' }),
  NEW_OFFER: translated({ icon: Tag, tone: BRAND }, { title: 'notifications.kinds.NEW_OFFER' }),
  OFFER_ACCEPTED: translated({ icon: BadgeCheck, tone: SUCCESS }, { title: 'notifications.kinds.OFFER_ACCEPTED' }),
  NEW_MESSAGE: translated({ icon: MessageSquare, tone: INFO }, { title: 'notifications.kinds.NEW_MESSAGE' }),
  TASK_STARTED: translated({ icon: Hammer, tone: INFO }, { title: 'notifications.kinds.TASK_STARTED' }),
  TASK_COMPLETED: translated({ icon: CircleCheck, tone: SUCCESS }, { title: 'notifications.kinds.TASK_COMPLETED' }),
  TASK_CLOSED: translated({ icon: CircleCheck, tone: SUCCESS }, { title: 'notifications.kinds.TASK_CLOSED' }),
  TASK_EXPIRED: translated({ icon: Hourglass, tone: WARNING }, { title: 'notifications.kinds.TASK_EXPIRED' }),
  REVIEW_RECEIVED: translated({ icon: Star, tone: WARNING }, { title: 'notifications.kinds.REVIEW_RECEIVED' }),
  TASK_REMOVED: translated({ icon: ShieldAlert, tone: DANGER }, { title: 'notifications.kinds.TASK_REMOVED' }),
  TASKER_WITHDREW: translated({ icon: UserMinus, tone: DANGER }, { title: 'notifications.kinds.TASKER_WITHDREW' }),
  ASSIGNMENT_RELEASED: translated({ icon: Undo2, tone: NEUTRAL }, { title: 'notifications.kinds.ASSIGNMENT_RELEASED' }),
  OFFER_REACTIVATED: translated({ icon: RotateCcw, tone: BRAND }, { title: 'notifications.kinds.OFFER_REACTIVATED' }),
  ASSIGNMENT_EXPIRED: translated({ icon: TimerOff, tone: WARNING }, { title: 'notifications.kinds.ASSIGNMENT_EXPIRED' }),
  TASK_AUTO_CLOSED: translated({ icon: Archive, tone: NEUTRAL }, { title: 'notifications.kinds.TASK_AUTO_CLOSED' }),
  TASK_UPDATED: translated({ icon: PencilLine, tone: INFO }, { title: 'notifications.kinds.TASK_UPDATED' }),
  OFFER_UPDATED: translated({ icon: Tag, tone: BRAND }, { title: 'notifications.kinds.OFFER_UPDATED' }),
};

export function notificationKind(type: AppNotification['type']): NotificationKind {
  return NOTIFICATION_KINDS[type as NotificationType] ?? translated({ icon: MapPin, tone: NEUTRAL }, { title: 'notifications.kinds.fallback' });
}

export function notificationLink(notification: AppNotification): string | null {
  const id = notification.relatedEntityId;
  if (!id) {
    return null;
  }
  return notification.type === 'NEW_MESSAGE'
    ? `/messages?conversation=${encodeURIComponent(id)}`
    : `/tasks/${encodeURIComponent(id)}`;
}

export type NotificationGroup = { label: string; items: AppNotification[] };

export function groupByDay(notifications: AppNotification[], now = new Date()): NotificationGroup[] {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const day = 24 * 60 * 60 * 1000;
  const labelOf = (date: string | undefined): string => {
    const created = date ? new Date(date).getTime() : 0;
    if (created >= today) return t('common.today');
    if (created >= today - day) return t('common.yesterday');
    if (created >= today - 6 * day) return t('time.thisWeek');
    return t('time.earlier');
  };
  const groups: NotificationGroup[] = [];
  for (const notification of notifications) {
    const label = labelOf(notification.createdAt);
    const last = groups.at(-1);
    if (last?.label === label) {
      last.items.push(notification);
    } else {
      groups.push({ label, items: [notification] });
    }
  }
  return groups;
}
