import { formatNumber, formatShortDate } from '../../i18n/formats';
import { t } from '../../i18n/translate';

const DAY_MS = 24 * 60 * 60 * 1000;

export function formatBudget(budget: number | null | undefined): string {
  if (budget == null) {
    return t('format.openBudget');
  }
  return `${formatNumber(budget)} KM`;
}

export function timeAgo(date: string | undefined, now = new Date()): string {
  if (!date) {
    return '';
  }
  const minutes = Math.floor((now.getTime() - new Date(date).getTime()) / 60_000);
  if (minutes < 1) return t('time.justNow');
  if (minutes < 60) return t('time.minutesAgo', { count: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t('time.hoursAgo', { count: hours });
  const days = Math.floor(hours / 24);
  if (days < 30) return days === 1 ? t('time.yesterday') : t('time.daysAgo', { count: days });
  return t('time.monthsAgo', { count: Math.floor(days / 30) });
}

export function daysLeft(date: string | undefined, now = new Date()): number | null {
  if (!date) {
    return null;
  }
  const calendarDays = Math.round((startOfDay(new Date(date)) - startOfDay(now)) / DAY_MS);
  return Math.max(0, calendarDays);
}

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

export function formatDate(date: string | undefined): string {
  return date ? formatShortDate(new Date(date)) : '';
}
