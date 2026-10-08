import { Component, EventEmitter, Output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { Ban, CircleAlert, ClipboardList, Flag, RotateCcw, ShieldCheck, UserRound, UserX, X } from 'lucide';
import { AdminReport, AdminReportPage } from '../../api/models';
import { Icon } from '../../components/icon/icon';
import { Pagination } from '../../components/pagination/pagination';
import { RemoveTaskDialog } from '../../components/remove-task-dialog/remove-task-dialog';
import { reasonKey } from '../../components/report-dialog/report-dialog';
import { AdminService } from '../../services/admin.service';
import { readApiError } from '../../shared/api-error';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { timeAgo } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { REPORT_FILTERS, ReportFilter, readReportFilter, readReportQuery, sameQuery } from './admin-query';
import { t } from '../../i18n/translate';
import { TranslatePipe } from '../../i18n/translate.pipe';

type Load = { page: AdminReportPage | null };

export function canRemoveReportedTask(report: AdminReport): boolean {
  return report.targetType === 'TASK' && (report.taskStatus === 'PUBLISHED' || report.taskStatus === 'ASSIGNED');
}

export function canSuspendReportedUser(report: AdminReport): boolean {
  return report.targetType === 'USER' && report.reportedUserStatus === 'ACTIVE';
}

@Component({
  selector: 'app-admin-reports',
  imports: [Icon, Pagination, RemoveTaskDialog, RouterLink, TranslatePipe],
  templateUrl: './admin-reports.html',
})
export class AdminReports {
  protected readonly icons = { Ban, CircleAlert, ClipboardList, Flag, RotateCcw, ShieldCheck, UserRound, UserX, X };

  @Output() changed = new EventEmitter<void>();

  readonly filters = REPORT_FILTERS;
  readonly skeletons = [1, 2, 3, 4];
  readonly timeAgo = timeAgo;
  readonly reasonKey = reasonKey;
  readonly canRemove = canRemoveReportedTask;
  readonly canSuspend = canSuspendReportedUser;

  readonly rows = signal<AdminReport[] | null>(null);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly filter = signal<ReportFilter>('open');
  readonly busy = signal<string | null>(null);
  readonly removing = signal<AdminReport | null>(null);

  private readonly retry$ = new BehaviorSubject<void>(undefined);

  constructor(
    private adminService: AdminService,
    private confirmService: ConfirmService,
    private toastService: ToastService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    const query$ = this.route.queryParamMap.pipe(
      map((params) => {
        this.filter.set(readReportFilter(params));
        return readReportQuery(params);
      }),
      distinctUntilChanged(sameQuery),
    );

    combineLatest([query$, this.retry$])
      .pipe(
        switchMap(([query]) => {
          this.failed.set(false);
          return this.adminService.reports(query).pipe(
            map((page): Load => ({ page })),
            catchError(() => of<Load>({ page: null })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ page }) => {
        if (!page) {
          this.rows.set(null);
          this.failed.set(true);
          return;
        }
        this.rows.set(page.content ?? []);
        this.page.set(page.page ?? 0);
        this.totalPages.set(page.totalPages ?? 0);
      });
  }

  targetName(report: AdminReport): string {
    return (report.targetType === 'TASK' ? report.taskTitle : report.reportedUserName) ?? '';
  }

  selectFilter(filter: ReportFilter): void {
    this.navigate({ filter: filter === 'open' ? null : filter, page: null });
  }

  goToPage(page: number): void {
    this.navigate({ page: page > 0 ? page + 1 : null });
  }

  retry(): void {
    this.retry$.next();
  }

  dismiss(report: AdminReport): void {
    this.run(report, this.adminService.dismissReport(report.id!), t('admin.reportDismissed'));
  }

  async suspend(report: AdminReport): Promise<void> {
    const confirmed = await this.confirmService.ask({
      title: t('admin.suspendTitle', { name: report.reportedUserName }),
      message: t('admin.suspendText'),
      confirmLabel: t('admin.suspendConfirm'),
      tone: 'danger',
    });
    if (confirmed) {
      this.run(report, this.adminService.suspend(report.reportedUserId!), t('admin.suspended', { name: report.reportedUserName }));
    }
  }

  onRemoved(): void {
    this.removing.set(null);
    this.toastService.success(t('admin.reportedTaskRemoved'));
    this.refresh();
  }

  private run(report: AdminReport, request: Observable<unknown>, message: string): void {
    this.busy.set(report.id!);
    request.subscribe({
      next: () => {
        this.busy.set(null);
        this.toastService.success(message);
        this.refresh();
      },
      error: (error) => {
        this.busy.set(null);
        this.toastService.error(readApiError(error).message);
      },
    });
  }

  private refresh(): void {
    this.retry$.next();
    this.changed.emit();
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }
}
