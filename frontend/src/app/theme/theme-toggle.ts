import { Component } from '@angular/core';
import { Moon, Sun } from 'lucide';
import { Icon } from '../components/icon/icon';
import { TranslatePipe } from '../i18n/translate.pipe';
import { ThemeService } from './theme.service';

@Component({
  selector: 'app-theme-toggle',
  imports: [Icon, TranslatePipe],
  template: `
    @let dark = themeService.theme() === 'dark';
    <button type="button" (click)="toggle($event)" class="icon-btn overflow-hidden"
            [title]="(dark ? 'common.lightTheme' : 'common.darkTheme') | t"
            [attr.aria-label]="(dark ? 'common.lightTheme' : 'common.darkTheme') | t">
      @if (dark) {
        <svg [appIcon]="icons.Sun" class="w-5 h-5 text-amber-400 animate-spin-in"></svg>
      } @else {
        <svg [appIcon]="icons.Moon" class="w-5 h-5 animate-spin-in"></svg>
      }
    </button>
  `,
})
export class ThemeToggle {
  readonly icons = { Moon, Sun };

  constructor(protected themeService: ThemeService) {}

  toggle(event: MouseEvent): void {
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.themeService.toggle({ x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2 });
  }
}
