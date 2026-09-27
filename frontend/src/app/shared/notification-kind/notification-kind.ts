import type { IconNode } from 'lucide';
import {
  Archive,
  BadgeCheck,
  CircleCheck,
  Hammer,
  Hourglass,
  MapPin,
  MessageSquare,
  RotateCcw,
  ShieldAlert,
  Star,
  Tag,
  TimerOff,
  Undo2,
  UserMinus,
} from 'lucide';
import { AppNotification, NotificationType } from '../../api/models';

export interface NotificationKind {
  title: string;
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
  NEW_TASK_IN_AREA: { title: 'New task near you', icon: MapPin, tone: BRAND },
  NEW_OFFER: { title: 'New offer', icon: Tag, tone: BRAND },
  OFFER_ACCEPTED: { title: 'You were hired', icon: BadgeCheck, tone: SUCCESS },
  NEW_MESSAGE: { title: 'New message', icon: MessageSquare, tone: INFO },
  TASK_STARTED: { title: 'Work started', icon: Hammer, tone: INFO },
  TASK_COMPLETED: { title: 'Work completed', icon: CircleCheck, tone: SUCCESS },
  TASK_CLOSED: { title: 'Task closed', icon: CircleCheck, tone: SUCCESS },
  TASK_EXPIRED: { title: 'Task expired', icon: Hourglass, tone: WARNING },
  REVIEW_RECEIVED: { title: 'New review', icon: Star, tone: WARNING },
  TASK_REMOVED: { title: 'Task removed', icon: ShieldAlert, tone: DANGER },
  TASKER_WITHDREW: { title: 'Tasker backed out', icon: UserMinus, tone: DANGER },
  ASSIGNMENT_RELEASED: { title: 'Task reopened by the client', icon: Undo2, tone: NEUTRAL },
  OFFER_REACTIVATED: { title: 'Offer active again', icon: RotateCcw, tone: BRAND },
  ASSIGNMENT_EXPIRED: { title: 'Task reopened', icon: TimerOff, tone: WARNING },
  TASK_AUTO_CLOSED: { title: 'Task closed automatically', icon: Archive, tone: NEUTRAL },
};

export function notificationKind(type: AppNotification['type']): NotificationKind {
  return NOTIFICATION_KINDS[type as NotificationType] ?? { title: 'Notification', icon: MapPin, tone: NEUTRAL };
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
    if (created >= today) return 'Today';
    if (created >= today - day) return 'Yesterday';
    if (created >= today - 6 * day) return 'This week';
    return 'Earlier';
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
