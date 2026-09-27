import { Component, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { LucideSquareCheck } from '@lucide/angular';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink, RouterOutlet, LucideSquareCheck],
  templateUrl: './auth-layout.html',
})
export class AuthLayout {
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
