import { Component, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  LucideCheck,
  LucideCircleAlert,
  LucideEye,
  LucideEyeOff,
  LucideLoaderCircle,
  LucideLock,
  LucideMail,
  LucidePhone,
} from '@lucide/angular';
import { AuthService } from '../../auth/auth.service';
import { safeReturnUrl } from '../../auth/return-url';
import { ApiError, readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-register',
  imports: [
    FormsModule,
    LucideCheck,
    LucideCircleAlert,
    LucideEye,
    LucideEyeOff,
    LucideLoaderCircle,
    LucideLock,
    LucideMail,
    LucidePhone,
  ],
  templateUrl: './register.html',
})
export class Register {
  readonly minPasswordLength = 8;

  firstName = '';
  lastName = '';
  email = '';
  phone = '';
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

  serverError(field: string): string | undefined {
    return this.error()?.fieldErrors[field];
  }

  clearServerError(field: string): void {
    const current = this.error();
    if (current?.fieldErrors[field]) {
      const fieldErrors = { ...current.fieldErrors };
      delete fieldErrors[field];
      this.error.set({ ...current, fieldErrors });
    }
  }

  submit(form: NgForm): void {
    if (form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.authService
      .register({
        firstName: this.firstName.trim(),
        lastName: this.lastName.trim(),
        email: this.email.trim(),
        phone: this.phone.trim() || undefined,
        password: this.password,
      })
      .subscribe({
        next: (user) => {
          this.toastService.success(`Welcome to TaskNest, ${user.fullName.split(' ')[0]}!`);
          this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')));
        },
        error: (error) => {
          this.error.set(readApiError(error));
          this.submitting.set(false);
        },
      });
  }
}
