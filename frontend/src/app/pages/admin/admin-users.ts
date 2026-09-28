import { Component, EventEmitter, Output, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subject, catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { BadgeCheck, CircleAlert, LoaderCircle, RotateCcw, Search, UserCheck, UserX, Users } from 'lucide';
import { AdminUser, AdminUserPage } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { Pagination } from '../../components/pagination/pagination';
import { AdminService } from '../../services/admin.service';
import { readApiError } from '../../shared/api-error';
import { avatarTone, initials } from '../../shared/chat/chat';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { formatDate } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { USER_FILTERS, UserFilter, readUserFilter, readUserQuery, sameQuery } from './admin-query';
import { t, translated } from '../../i18n/translate';
import { TranslatePipe } from '../../i18n/translate.pipe';

export const ACCOUNT_STATUS: Record<string, { readonly label: string; badge: string }> = {
  ACTIVE: translated({ badge: 'bg-green-50 text-green-800 border-green-200' }, { label: 'admin.accountACTIVE' }),
  SUSPENDED: translated({ badge: 'bg-red-50 text-red-700 border-red-200' }, { label: 'admin.accountSUSPENDED' }),
  DEACTIVATED: translated({ badge: 'bg-slate-100 text-slate-600 border-slate-200' }, { label: 'admin.accountDEACTIVATED' }),
};

type Load = { page: AdminUserPage | null };

@Component({
  selector: 'app-admin-users',
  imports: [Icon, Pagination, TranslatePipe],
  templateUrl: './admin-users.html',
})
export class AdminUsers {
  protected readonly icons = { BadgeCheck, CircleAlert, LoaderCircle, RotateCcw, Search, UserCheck, UserX, Users };

  @Output() changed = new EventEmitter<void>();

  readonly filters = USER_FILTERS;
  readonly skeletons = [1, 2, 3, 4, 5];
  readonly accountStatus = ACCOUNT_STATUS;
  readonly avatarTone = avatarTone;
  readonly initials = initials;
  readonly formatDate = formatDate;

  readonly rows = signal<AdminUser[] | null>(null);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly busy = signal<string | null>(null);
  readonly filter = signal<UserFilter>('all');
  readonly search = signal('');
  readonly myId = computed(() => this.authService.currentUser?.id);

  private readonly retry$ = new BehaviorSubject<void>(undefined);
  private readonly typing$ = new Subject<string>();

  constructor(
    private adminService: AdminService,
    private authService: AuthService,
    private confirmService: ConfirmService,
    private toastService: ToastService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    const query$ = this.route.queryParamMap.pipe(
      map((params) => {
        this.filter.set(readUserFilter(params));
        return readUserQuery(params);
      }),
      distinctUntilChanged(sameQuery),
    );

    combineLatest([query$, this.retry$])
      .pipe(
        switchMap(([query]) => {
          this.search.set(query.search);
          this.rows.set(null);
          this.failed.set(false);
          return this.adminService.users(query).pipe(
            map((page): Load => ({ page })),
            catchError(() => of<Load>({ page: null })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ page }) => {
        if (!page) {
          this.failed.set(true);
          return;
        }
        this.rows.set(page.content ?? []);
        this.page.set(page.page ?? 0);
        this.totalPages.set(page.totalPages ?? 0);
        this.total.set(page.totalElements ?? 0);
      });

    this.typing$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((text) => this.navigate({ q: text.trim() || null, page: null }, true));
  }

  onSearch(text: string): void {
    this.search.set(text);
    this.typing$.next(text);
  }

  selectFilter(filter: UserFilter): void {
    this.navigate({ filter: filter === 'all' ? null : filter, page: null });
  }

  goToPage(page: number): void {
    this.navigate({ page: page > 0 ? page + 1 : null });
  }

  retry(): void {
    this.retry$.next();
  }

  canModerate(user: AdminUser): boolean {
    return user.id !== this.myId() && !(user.roles ?? []).includes('ADMIN');
  }

  isTasker(user: AdminUser): boolean {
    return (user.roles ?? []).includes('TASKER');
  }

  roleLabel(user: AdminUser): string {
    const roles = user.roles ?? [];
    if (roles.includes('ADMIN')) return t('admin.roleAdmin');
    return roles.includes('TASKER') ? t('admin.roleTasker') : t('admin.roleClient');
  }

  async suspend(user: AdminUser): Promise<void> {
    const confirmed = await this.confirmService.ask({
      title: t('admin.suspendTitle', { name: user.fullName }),
      message: t('admin.suspendText'),
      confirmLabel: t('admin.suspendConfirm'),
      tone: 'danger',
    });
    if (confirmed) {
      this.act(user, this.adminService.suspend(user.id!), t('admin.suspended', { name: user.fullName }));
    }
  }

  reactivate(user: AdminUser): void {
    this.act(user, this.adminService.reactivate(user.id!), t('admin.reactivated', { name: user.fullName }));
  }

  toggleVerified(user: AdminUser): void {
    if (!user.taskerProfileId || this.busy()) {
      return;
    }
    const verified = !user.taskerVerified;
    this.busy.set(user.id!);
    this.adminService.setVerified(user.taskerProfileId, verified).subscribe({
      next: () => {
        this.busy.set(null);
        this.replace({ ...user, taskerVerified: verified });
        this.toastService.success(t(verified ? 'admin.verified' : 'admin.unverified', { name: user.fullName }));
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(null);
        this.toastService.error(readApiError(error).message);
      },
    });
  }

  private act(user: AdminUser, request: ReturnType<AdminService['suspend']>, message: string): void {
    this.busy.set(user.id!);
    request.subscribe({
      next: (updated) => {
        this.busy.set(null);
        this.replace({ ...user, ...updated });
        this.toastService.success(message);
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(null);
        this.toastService.error(readApiError(error).message);
      },
    });
  }

  private replace(user: AdminUser): void {
    this.rows.update((rows) => rows?.map((row) => (row.id === user.id ? user : row)) ?? rows);
  }

  private navigate(queryParams: Record<string, string | number | null>, replaceUrl = false): void {
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl });
  }
}
