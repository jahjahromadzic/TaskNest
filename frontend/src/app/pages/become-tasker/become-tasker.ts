import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ArrowRight, LoaderCircle, Star, Target, UserPen, Wallet } from 'lucide';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey, t, translated } from '../../i18n/translate';

@Component({
  selector: 'app-become-tasker',
  imports: [Icon, TranslatePipe],
  templateUrl: './become-tasker.html',
})
export class BecomeTasker {
  protected readonly icons = { ArrowRight, LoaderCircle, Star, Target, UserPen, Wallet };

  readonly activating = signal(false);

  readonly benefits = [
    translated({ icon: Target }, { title: 'becomeTasker.benefit1Title', text: 'becomeTasker.benefit1Text' }),
    translated({ icon: Wallet }, { title: 'becomeTasker.benefit2Title', text: 'becomeTasker.benefit2Text' }),
    translated({ icon: Star }, { title: 'becomeTasker.benefit3Title', text: 'becomeTasker.benefit3Text' }),
  ];

  readonly steps: TranslationKey[] = ['becomeTasker.step1', 'becomeTasker.step2', 'becomeTasker.step3'];

  constructor(
    private authService: AuthService,
    private toastService: ToastService,
    private router: Router,
  ) {}

  activate(): void {
    if (this.activating()) {
      return;
    }
    this.activating.set(true);
    this.authService.becomeTasker().subscribe({
      next: () => {
        this.toastService.success(t('becomeTasker.welcome'));
        this.router.navigate(['/tasker/profile'], { queryParams: { welcome: true } });
      },
      error: (error) => {
        this.activating.set(false);
        this.toastService.error(readApiError(error).message);
      },
    });
  }
}
