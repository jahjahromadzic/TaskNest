const DAY_MS = 24 * 60 * 60 * 1000;

const budgetFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 });

export function formatBudget(budget: number | null | undefined): string {
  return budget == null ? 'Open budget' : `${budgetFormat.format(budget)} KM`;
}

export function timeAgo(date: string | undefined, now = new Date()): string {
  if (!date) {
    return '';
  }
  const minutes = Math.floor((now.getTime() - new Date(date).getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours === 1 ? '1 hour ago' : `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return days === 1 ? 'yesterday' : `${days} days ago`;
  const months = Math.floor(days / 30);
  return months === 1 ? '1 month ago' : `${months} months ago`;
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

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

export function formatDate(date: string | undefined): string {
  return date ? dateFormat.format(new Date(date)) : '';
}
