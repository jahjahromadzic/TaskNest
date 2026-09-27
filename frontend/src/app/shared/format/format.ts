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
  return Math.max(0, Math.ceil((new Date(date).getTime() - now.getTime()) / DAY_MS));
}
