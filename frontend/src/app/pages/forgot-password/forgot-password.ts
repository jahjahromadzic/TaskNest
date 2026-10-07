import { Component, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ArrowLeft, CircleAlert, LoaderCircle, Mail, MailCheck } from 'lucide';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { ApiError, readApiError } from '../../shared/api-error';
import { TranslatePipe } from '../../i18n/translate.pipe';

@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, Icon, RouterLink, TranslatePipe],
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  protected readonly icons = { ArrowLeft, CircleAlert, LoaderCircle, Mail, MailCheck };

  email = '';

  readonly submitting = signal(false);
  readonly sentTo = signal<string | null>(null);
  readonly error = signal<ApiError | null>(null);

  constructor(private authService: AuthService) {}

  submit(form: NgForm): void {
    if (form.invalid || this.submitting()) {
      return;
    }
    const email = this.email.trim();
    this.submitting.set(true);
    this.error.set(null);
    this.authService.requestPasswordReset(email).subscribe({
      next: () => {
        this.submitting.set(false);
        this.sentTo.set(email);
      },
      error: (error) => {
        this.submitting.set(false);
        this.error.set(readApiError(error));
      },
    });
  }
}
