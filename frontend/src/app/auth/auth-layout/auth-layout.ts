import { Component, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { SquareCheck } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey } from '../../i18n/translate';

type AuthMode = 'login' | 'register' | 'forgot-password' | 'reset-password';

const TITLES: Record<AuthMode, [TranslationKey, TranslationKey]> = {
  login: ['auth.loginTitle', 'auth.loginSubtitle'],
  register: ['auth.registerTitle', 'auth.registerSubtitle'],
  'forgot-password': ['auth.forgotTitle', 'auth.forgotSubtitle'],
  'reset-password': ['auth.resetTitle', 'auth.resetSubtitle'],
};

@Component({
  selector: 'app-auth-layout',
  imports: [Icon, RouterLink, RouterOutlet, TranslatePipe],
  templateUrl: './auth-layout.html',
})
export class AuthLayout {
  protected readonly icons = { SquareCheck };

  readonly mode: Signal<AuthMode>;
  readonly titles = TITLES;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
  ) {
    this.mode = toSignal(
      this.router.events.pipe(
        filter((event) => event instanceof NavigationEnd),
        map(() => this.currentMode()),
      ),
      { initialValue: this.currentMode() },
    );
  }

  get switchable(): boolean {
    return this.mode() === 'login' || this.mode() === 'register';
  }

  private currentMode(): AuthMode {
    const path = this.route.firstChild?.routeConfig?.path;
    return path === 'register' || path === 'forgot-password' || path === 'reset-password' ? path : 'login';
  }
}
