import { currentLang } from './lang';

const BS_MONTHS = ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul', 'aug', 'sep', 'okt', 'nov', 'dec'];
const BS_WEEKDAYS = ['ned', 'pon', 'uto', 'sri', 'čet', 'pet', 'sub'];

export function formatNumber(value: number, maximumFractionDigits = 2): string {
  const english = new Intl.NumberFormat('en-US', { maximumFractionDigits }).format(value);
  if (currentLang() === 'en') {
    return english;
  }
  return english.replace(/[,.]/g, (separator) => (separator === ',' ? '.' : ','));
}

export function formatShortDate(date: Date): string {
  if (currentLang() === 'en') {
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
  }
  return `${date.getDate()}. ${BS_MONTHS[date.getMonth()]} ${date.getFullYear()}.`;
}

export function formatWeekdayDate(date: Date): string {
  if (currentLang() === 'en') {
    return new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' }).format(date);
  }
  return `${BS_WEEKDAYS[date.getDay()]}, ${date.getDate()}. ${BS_MONTHS[date.getMonth()]}`;
}

export function formatClock(date: Date): string {
  return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' }).format(date);
}
