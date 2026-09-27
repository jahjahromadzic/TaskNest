import { Component, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CircleAlert, Eye, EyeOff, LoaderCircle, Lock, Mail } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { AuthService } from '../../auth/auth.service';
import { safeReturnUrl } from '../../auth/return-url';
import { ApiError, readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-login',
  imports: [Icon, FormsModule],
  templateUrl: './login.html',
})
export class Login {
  protected readonly icons = { CircleAlert, Eye, EyeOff, LoaderCircle, Lock, Mail };

  email = '';
  password = '';

  readonly showPassword = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<ApiError | null>(null);

  constructor(
    private authService: AuthService,
    private toastService: ToastService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  submit(form: NgForm): void {
    if (form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.authService.login({ email: this.email.trim(), password: this.password }).subscribe({
      next: (user) => {
        this.toastService.success(`Welcome back, ${user.fullName.split(' ')[0]}!`);
        this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')));
      },
      error: (error) => {
        this.error.set(readApiError(error));
        this.submitting.set(false);
      },
    });
  }
}
