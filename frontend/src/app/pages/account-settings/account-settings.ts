import { Component, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CalendarDays,
  CircleAlert,
  CircleUserRound,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  Lock,
  Mail,
  Phone,
  RotateCcw,
  Save,
  ShieldCheck,
} from 'lucide';
import { Account } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { AccountService } from '../../services/account.service';
import { ApiError, readApiError } from '../../shared/api-error';
import { formatDate } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';
import { MIN_PASSWORD_LENGTH } from '../reset-password/reset-password';

export const NAME_MAX = 100;
export const PHONE_MAX = 30;

@Component({
  selector: 'app-account-settings',
  imports: [FormsModule, Icon, RouterLink, TranslatePipe],
  templateUrl: './account-settings.html',
})
export class AccountSettings {
  protected readonly icons = {
    CalendarDays,
    CircleAlert,
    CircleUserRound,
    Eye,
    EyeOff,
    KeyRound,
    LoaderCircle,
    Lock,
    Mail,
    Phone,
    RotateCcw,
    Save,
    ShieldCheck,
  };
  readonly nameMax = NAME_MAX;
  readonly phoneMax = PHONE_MAX;
  readonly minLength = MIN_PASSWORD_LENGTH;
  readonly formatDate = formatDate;

  readonly account = signal<Account | null>(null);
  readonly loadFailed = signal(false);

  firstName = '';
  lastName = '';
  phone = '';
  readonly savingDetails = signal(false);
  readonly detailsError = signal<ApiError | null>(null);

  currentPassword = '';
  newPassword = '';
  repeated = '';
  readonly showPasswords = signal(false);
  readonly savingPassword = signal(false);
  readonly passwordError = signal<ApiError | null>(null);

  constructor(
    private accountService: AccountService,
    private authService: AuthService,
    private toastService: ToastService,
  ) {
    this.load();
  }

  load(): void {
    this.loadFailed.set(false);
    this.accountService.getAccount().subscribe({
      next: (account) => this.fill(account),
      error: () => this.loadFailed.set(true),
    });
  }

  get detailsChanged(): boolean {
    const account = this.account();
    return (
      account !== null &&
      (this.firstName.trim() !== (account.firstName ?? '') ||
        this.lastName.trim() !== (account.lastName ?? '') ||
        this.phone.trim() !== (account.phone ?? ''))
    );
  }

  get mismatch(): boolean {
    return this.repeated.length > 0 && this.repeated !== this.newPassword;
  }

  get sameAsCurrent(): boolean {
    return this.newPassword.length > 0 && this.newPassword === this.currentPassword;
  }

  initials(account: Account): string {
    return [account.firstName, account.lastName]
      .map((part) => (part ?? '').trim().charAt(0).toUpperCase())
      .join('');
  }

  saveDetails(form: NgForm): void {
    if (form.invalid || !this.detailsChanged || this.savingDetails()) {
      return;
    }
    this.savingDetails.set(true);
    this.detailsError.set(null);
    this.accountService
      .updateAccount({
        firstName: this.firstName.trim(),
        lastName: this.lastName.trim(),
        phone: this.phone.trim() || undefined,
      })
      .subscribe({
        next: (account) => {
          this.fill(account);
          this.savingDetails.set(false);
          this.toastService.success(t('settings.detailsSaved'));
        },
        error: (error) => {
          this.savingDetails.set(false);
          this.detailsError.set(readApiError(error));
        },
      });
  }

  changePassword(form: NgForm): void {
    if (form.invalid || this.mismatch || this.sameAsCurrent || this.savingPassword()) {
      return;
    }
    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.authService.changePassword({ currentPassword: this.currentPassword, newPassword: this.newPassword }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.showPasswords.set(false);
        form.resetForm({ currentPassword: '', newPassword: '', repeated: '' });
        this.toastService.success(t('settings.passwordChanged'));
      },
      error: (error) => {
        this.savingPassword.set(false);
        this.passwordError.set(readApiError(error));
      },
    });
  }

  private fill(account: Account): void {
    this.account.set(account);
    this.firstName = account.firstName ?? '';
    this.lastName = account.lastName ?? '';
    this.phone = account.phone ?? '';
  }
}
