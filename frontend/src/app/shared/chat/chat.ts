import { ChatMessage } from '../../api/models';

export interface PendingMessage extends ChatMessage {
  state?: 'sending' | 'failed';
}

export type ThreadItem =
  | { kind: 'day'; key: string; label: string }
  | { kind: 'message'; key: string; message: PendingMessage; mine: boolean; startsGroup: boolean };

const DAY_MS = 24 * 60 * 60 * 1000;
const GROUP_GAP_MS = 5 * 60 * 1000;
const dayFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
const timeFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

export function dayLabel(date: string | undefined, now = new Date()): string {
  if (!date) {
    return 'Today';
  }
  const day = startOfDay(new Date(date));
  const today = startOfDay(now);
  if (day === today) return 'Today';
  if (day === today - DAY_MS) return 'Yesterday';
  return dayFormat.format(new Date(date));
}

export function clockTime(date: string | undefined): string {
  return date ? timeFormat.format(new Date(date)) : '';
}

export function buildThread(messages: PendingMessage[], myId: string | undefined, now = new Date()): ThreadItem[] {
  const items: ThreadItem[] = [];
  let previous: PendingMessage | null = null;
  let previousDay = '';
  for (const message of messages) {
    const label = dayLabel(message.createdAt, now);
    const newDay = label !== previousDay;
    if (newDay) {
      items.push({ kind: 'day', key: `day-${message.createdAt ? startOfDay(new Date(message.createdAt)) : 'now'}`, label });
      previousDay = label;
    }
    const gap =
      previous?.createdAt && message.createdAt
        ? new Date(message.createdAt).getTime() - new Date(previous.createdAt).getTime()
        : 0;
    const startsGroup = newDay || previous?.senderId !== message.senderId || gap > GROUP_GAP_MS;
    items.push({ kind: 'message', key: message.id ?? '', message, mine: message.senderId === myId, startsGroup });
    previous = message;
  }
  return items;
}

export function localTimestamp(date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

export function mergeMessages(current: PendingMessage[], incoming: PendingMessage[]): PendingMessage[] {
  const known = new Set(current.map((message) => message.id));
  return [...incoming.filter((message) => !known.has(message.id)), ...current];
}

export function preview(content: string | undefined, mine: boolean): string {
  if (!content) {
    return '';
  }
  return mine ? `You: ${content}` : content;
}

const AVATAR_TONES = [
  'bg-teal-600',
  'bg-sky-600',
  'bg-violet-600',
  'bg-amber-600',
  'bg-rose-600',
  'bg-emerald-600',
  'bg-indigo-600',
  'bg-slate-700',
];

export function avatarTone(name: string | undefined): string {
  let hash = 0;
  for (const char of name ?? '') {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }
  return AVATAR_TONES[hash % AVATAR_TONES.length];
}

export function initials(name: string | undefined): string {
  return (name ?? '')
    .split(' ')
    .filter((part) => part.length > 0)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}
