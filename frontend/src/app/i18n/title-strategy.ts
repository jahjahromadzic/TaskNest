import { Injectable, effect } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { currentLang } from './lang';
import { TranslationKey, t } from './translate';

export function pageTitle(text: string): string {
  return `${text} · TaskNest`;
}

@Injectable({ providedIn: 'root' })
export class TranslatedTitleStrategy extends TitleStrategy {
  private key: TranslationKey | null = null;
  private applied = '';

  constructor(private title: Title) {
    super();
    effect(() => {
      currentLang();
      if (this.key && this.title.getTitle() === this.applied) {
        this.apply(this.key);
      }
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const key = this.buildTitle(snapshot) as TranslationKey | undefined;
    if (key) {
      this.key = key;
      this.apply(key);
    }
  }

  private apply(key: TranslationKey): void {
    this.applied = pageTitle(t(key));
    this.title.setTitle(this.applied);
  }
}
