import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ArrowRight, LoaderCircle, Star, Target, UserPen, Wallet } from 'lucide';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-become-tasker',
  imports: [Icon],
  templateUrl: './become-tasker.html',
})
export class BecomeTasker {
  protected readonly icons = { ArrowRight, LoaderCircle, Star, Target, UserPen, Wallet };

  readonly activating = signal(false);

  readonly benefits = [
    { icon: Target, title: 'Only jobs that fit you', text: 'You see tasks in the categories and municipalities you choose.' },
    { icon: Wallet, title: 'You set the price', text: 'Send your own offer on any open task and explain what it includes.' },
    { icon: Star, title: 'Grow your reputation', text: 'Every finished job and review makes clients trust you more.' },
  ];

  readonly steps = ['Activate your tasker account', 'Choose your categories and areas', 'Send offers and get hired'];

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
        this.toastService.success('Welcome aboard! Now tell clients what you do.');
        this.router.navigate(['/tasker/profile'], { queryParams: { welcome: true } });
      },
      error: (error) => {
        this.activating.set(false);
        this.toastService.error(readApiError(error).message);
      },
    });
  }
}
