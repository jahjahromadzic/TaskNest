import { Component, ElementRef, HostListener, signal } from '@angular/core';
import { NavigationStart, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { ArrowRight, Bell, BellOff, CheckCheck } from 'lucide';
import { AppNotification } from '../../api/models';
import { Icon } from '../../components/icon/icon';
import { NotificationItem } from '../../components/notification-item/notification-item';
import { NotificationService } from '../../services/notification.service';
import { ToastService } from '../../shared/toast/toast.service';

export const BELL_PREVIEW_SIZE = 5;

@Component({
  selector: 'app-notification-bell',
  imports: [Icon, NotificationItem, RouterLink],
  templateUrl: './notification-bell.html',
})
export class NotificationBell {
  protected readonly icons = { ArrowRight, Bell, BellOff, CheckCheck };

  readonly open = signal(false);
  readonly items = signal<AppNotification[] | null>(null);
  readonly failed = signal(false);
  readonly skeletons = [1, 2, 3];

  constructor(
    protected notificationService: NotificationService,
    private toastService: ToastService,
    private host: ElementRef<HTMLElement>,
    router: Router,
  ) {
    router.events.pipe(filter((event) => event instanceof NavigationStart)).subscribe(() => this.open.set(false));
  }

  badge(count: number): string {
    return count > 9 ? '9+' : String(count);
  }

  toggle(): void {
    if (this.open()) {
      this.open.set(false);
      return;
    }
    this.open.set(true);
    this.load();
  }

  load(): void {
    this.items.set(null);
    this.failed.set(false);
    this.notificationService.list(0, BELL_PREVIEW_SIZE).subscribe({
      next: (page) => this.items.set(page.content ?? []),
      error: () => this.failed.set(true),
    });
  }

  select(notification: AppNotification): void {
    this.open.set(false);
    this.notificationService.open(notification);
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe({
      next: () => this.items.update((items) => items?.map((item) => ({ ...item, read: true })) ?? null),
      error: () => this.toastService.error('Could not mark your notifications as read. Please try again.'),
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}
