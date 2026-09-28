import { Component, OnInit, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Bell, BellOff, CheckCheck, CircleAlert, LoaderCircle, RotateCcw } from 'lucide';
import { AppNotification } from '../../api/models';
import { Icon } from '../../components/icon/icon';
import { NotificationItem } from '../../components/notification-item/notification-item';
import { NotificationService } from '../../services/notification.service';
import { RealtimeService } from '../../services/realtime.service';
import { groupByDay } from '../../shared/notification-kind/notification-kind';
import { ToastService } from '../../shared/toast/toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

export const NOTIFICATIONS_PER_PAGE = 20;

@Component({
  selector: 'app-notifications',
  imports: [Icon, NotificationItem, TranslatePipe],
  templateUrl: './notifications.html',
})
export class Notifications implements OnInit {
  protected readonly icons = { Bell, BellOff, CheckCheck, CircleAlert, LoaderCircle, RotateCcw };

  readonly items = signal<AppNotification[] | null>(null);
  readonly total = signal(0);
  readonly failed = signal(false);
  readonly loadingMore = signal(false);
  readonly markingAll = signal(false);
  readonly groups = computed(() => groupByDay(this.items() ?? []));
  readonly hasMore = computed(() => (this.items()?.length ?? 0) < this.total());
  readonly skeletons = [1, 2, 3, 4];

  private page = 0;

  constructor(
    protected notificationService: NotificationService,
    private toastService: ToastService,
    realtime: RealtimeService,
  ) {
    realtime.notifications$.pipe(takeUntilDestroyed()).subscribe((notification) => {
      const items = this.items();
      if (items !== null && !items.some((item) => item.id === notification.id)) {
        this.items.set([notification, ...items]);
        this.total.update((total) => total + 1);
      }
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.failed.set(false);
    this.items.set(null);
    this.notificationService.list(0, NOTIFICATIONS_PER_PAGE).subscribe({
      next: (page) => {
        this.page = 0;
        this.items.set(page.content ?? []);
        this.total.set(page.totalElements ?? 0);
      },
      error: () => this.failed.set(true),
    });
  }

  showMore(): void {
    if (this.loadingMore() || !this.hasMore()) {
      return;
    }
    this.loadingMore.set(true);
    this.notificationService.list(this.page + 1, NOTIFICATIONS_PER_PAGE).subscribe({
      next: (next) => {
        this.page++;
        const known = new Set((this.items() ?? []).map((item) => item.id));
        const fresh = (next.content ?? []).filter((item) => !known.has(item.id));
        this.items.update((items) => [...(items ?? []), ...fresh]);
        this.loadingMore.set(false);
      },
      error: () => {
        this.loadingMore.set(false);
        this.toastService.error(t('notifications.loadMoreFailed'));
      },
    });
  }

  select(notification: AppNotification): void {
    this.items.update((items) => items?.map((item) => (item.id === notification.id ? { ...item, read: true } : item)) ?? null);
    this.notificationService.open(notification);
  }

  markAllRead(): void {
    this.markingAll.set(true);
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.markingAll.set(false);
        this.items.update((items) => items?.map((item) => ({ ...item, read: true })) ?? null);
        this.toastService.success(t('notifications.allRead'));
      },
      error: () => {
        this.markingAll.set(false);
        this.toastService.error(t('notifications.markFailed'));
      },
    });
  }
}
