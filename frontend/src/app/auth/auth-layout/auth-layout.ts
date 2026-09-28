import { Component, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { SquareCheck } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-auth-layout',
  imports: [Icon, RouterLink, RouterOutlet, TranslatePipe],
  templateUrl: './auth-layout.html',
})
export class AuthLayout {
  protected readonly icons = { SquareCheck };

  readonly mode: Signal<AuthMode>;

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

  private currentMode(): AuthMode {
    return this.route.firstChild?.routeConfig?.path === 'register' ? 'register' : 'login';
  }
}
