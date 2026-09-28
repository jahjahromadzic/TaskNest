import { signal } from '@angular/core';

export type Lang = 'en' | 'bs';

export const LANGS: { code: Lang; label: string; name: string }[] = [
  { code: 'en', label: 'EN', name: 'English' },
  { code: 'bs', label: 'BS', name: 'Bosanski' },
];

export const currentLang = signal<Lang>('en');

export type PluralForm = 'one' | 'few' | 'other';

export function pluralForm(count: number, lang: Lang): PluralForm {
  const whole = Math.abs(Math.trunc(count));
  if (lang === 'en') {
    return whole === 1 ? 'one' : 'other';
  }
  const lastDigit = whole % 10;
  const lastTwo = whole % 100;
  if (lastDigit === 1 && lastTwo !== 11) return 'one';
  if (lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14)) return 'few';
  return 'other';
}
