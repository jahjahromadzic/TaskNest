import { Component, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CircleAlert, Eye, EyeOff, LoaderCircle, Lock } from 'lucide';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { ApiError, readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

export const MIN_PASSWORD_LENGTH = 8;

@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, Icon, RouterLink, TranslatePipe],
  templateUrl: './reset-password.html',
})
export class ResetPassword {
  protected readonly icons = { CircleAlert, Eye, EyeOff, LoaderCircle, Lock };
  readonly minLength = MIN_PASSWORD_LENGTH;
  readonly token: string;

  password = '';
  repeated = '';

  readonly showPassword = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<ApiError | null>(null);

  constructor(
    private authService: AuthService,
    private toastService: ToastService,
    private router: Router,
    route: ActivatedRoute,
  ) {
    this.token = route.snapshot.queryParamMap.get('token') ?? '';
  }

  get mismatch(): boolean {
    return this.repeated.length > 0 && this.repeated !== this.password;
  }

  submit(form: NgForm): void {
    if (form.invalid || this.mismatch || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.authService.resetPassword(this.token, this.password).subscribe({
      next: () => {
        this.toastService.success(t('auth.passwordChanged'));
        this.router.navigate(['/login'], { replaceUrl: true });
      },
      error: (error) => {
        this.submitting.set(false);
        this.error.set(readApiError(error));
      },
    });
  }
}
