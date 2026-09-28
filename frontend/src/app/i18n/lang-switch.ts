import { Component, computed } from '@angular/core';
import { Languages } from 'lucide';
import { Icon } from '../components/icon/icon';
import { I18nService } from './i18n.service';
import { LANGS } from './lang';
import { TranslatePipe } from './translate.pipe';

@Component({
  selector: 'app-lang-switch',
  imports: [Icon, TranslatePipe],
  template: `
    <button type="button" (click)="i18n.use(next().code)"
            class="icon-btn inline-flex items-center gap-1.5 overflow-hidden"
            [title]="'common.switchLanguage' | t: { name: next().name }"
            [attr.aria-label]="'common.switchLanguage' | t: { name: next().name }">
      <svg [appIcon]="icons.Languages" class="w-5 h-5"></svg>
      @switch (i18n.lang()) {
        @case ('bs') {
          <span class="w-5 text-left text-xs font-bold animate-fade-in" lang="bs">BS</span>
        }
        @default {
          <span class="w-5 text-left text-xs font-bold animate-fade-in" lang="en">EN</span>
        }
      }
    </button>
  `,
})
export class LangSwitch {
  readonly icons = { Languages };
  protected readonly next = computed(() => LANGS.find((lang) => lang.code !== this.i18n.lang()) ?? LANGS[0]);

  constructor(protected i18n: I18nService) {}
}
