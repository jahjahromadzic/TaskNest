import { Component } from '@angular/core';
import { I18nService } from './i18n.service';
import { LANGS } from './lang';
import { TranslatePipe } from './translate.pipe';

@Component({
  selector: 'app-lang-switch',
  imports: [TranslatePipe],
  template: `
    <div role="group" [attr.aria-label]="'common.language' | t"
         class="relative grid grid-cols-2 p-0.5 bg-slate-100 rounded-lg text-[11px] font-bold">
      <span class="absolute inset-y-0.5 left-0.5 w-[calc(50%-0.125rem)] bg-white rounded-md shadow-subtle transition-transform duration-300 ease-out-soft"
            [class.translate-x-full]="i18n.lang() === 'bs'" aria-hidden="true"></span>
      @for (lang of langs; track lang.code) {
        <button type="button" (click)="i18n.use(lang.code)" [attr.aria-pressed]="i18n.lang() === lang.code"
                [title]="lang.name" [attr.lang]="lang.code"
                class="relative z-10 px-2 py-1 rounded-md transition-colors duration-200"
                [class]="i18n.lang() === lang.code ? 'text-brand' : 'text-slate-500 hover:text-text-main'">
          {{ lang.label }}
        </button>
      }
    </div>
  `,
})
export class LangSwitch {
  readonly langs = LANGS;

  constructor(protected i18n: I18nService) {}
}
