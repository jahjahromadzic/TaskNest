import { Injectable } from '@angular/core';
import { Lang, currentLang } from './lang';

export const LANG_STORAGE_KEY = 'tasknest-lang';

export function preferredLang(stored: string | null, browserLanguages: readonly string[]): Lang {
  if (stored === 'en' || stored === 'bs') {
    return stored;
  }
  const local = browserLanguages.some((language) => /^(bs|hr|sr|sh)\b/i.test(language));
  return local ? 'bs' : 'en';
}

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = currentLang.asReadonly();

  start(): void {
    this.apply(preferredLang(readStored(), navigator.languages ?? [navigator.language]));
  }

  use(lang: Lang): void {
    this.apply(lang);
    try {
      localStorage.setItem(LANG_STORAGE_KEY, lang);
    } catch {
      return;
    }
  }

  private apply(lang: Lang): void {
    currentLang.set(lang);
    document.documentElement.lang = lang;
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(LANG_STORAGE_KEY);
  } catch {
    return null;
  }
}
