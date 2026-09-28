import { Component, EventEmitter, Output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Subject, catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { Ban, CircleAlert, ClipboardList, RotateCcw, Search } from 'lucide';
import { AdminTask, AdminTaskPage, TaskDetail } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { Pagination } from '../../components/pagination/pagination';
import { RemoveTaskDialog } from '../../components/remove-task-dialog/remove-task-dialog';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { AdminService } from '../../services/admin.service';
import { formatBudget, timeAgo } from '../../shared/format/format';
import { TaskStatus } from '../../shared/task-status/task-status';
import { ToastService } from '../../shared/toast/toast.service';
import { TASK_FILTERS, TaskFilter, readTaskFilter, readTaskQuery, sameQuery } from './admin-query';
import { t } from '../../i18n/translate';
import { TranslatePipe } from '../../i18n/translate.pipe';

type Load = { page: AdminTaskPage | null };

export function canRemove(status: AdminTask['status']): boolean {
  return status === 'PUBLISHED' || status === 'ASSIGNED';
}

@Component({
  selector: 'app-admin-tasks',
  imports: [CategoryIcon, Icon, Pagination, RemoveTaskDialog, RouterLink, StatusBadge, TranslatePipe],
  templateUrl: './admin-tasks.html',
})
export class AdminTasks {
  protected readonly icons = { Ban, CircleAlert, ClipboardList, RotateCcw, Search };

  @Output() changed = new EventEmitter<void>();

  readonly filters = TASK_FILTERS;
  readonly skeletons = [1, 2, 3, 4, 5];
  readonly formatBudget = formatBudget;
  readonly timeAgo = timeAgo;
  readonly canRemove = canRemove;

  readonly rows = signal<AdminTask[] | null>(null);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly filter = signal<TaskFilter>('all');
  readonly search = signal('');
  readonly removing = signal<AdminTask | null>(null);

  private readonly retry$ = new BehaviorSubject<void>(undefined);
  private readonly typing$ = new Subject<string>();

  constructor(
    private toastService: ToastService,
    private route: ActivatedRoute,
    private router: Router,
    adminService: AdminService,
  ) {
    const query$ = this.route.queryParamMap.pipe(
      map((params) => {
        this.filter.set(readTaskFilter(params));
        return readTaskQuery(params);
      }),
      distinctUntilChanged(sameQuery),
    );

    combineLatest([query$, this.retry$])
      .pipe(
        switchMap(([query]) => {
          this.search.set(query.search);
          this.rows.set(null);
          this.failed.set(false);
          return adminService.tasks(query).pipe(
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
      });

    this.typing$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((text) => this.navigate({ q: text.trim() || null, page: null }, true));
  }

  status(task: AdminTask): TaskStatus | undefined {
    return task.status as TaskStatus | undefined;
  }

  onSearch(text: string): void {
    this.search.set(text);
    this.typing$.next(text);
  }

  selectFilter(filter: TaskFilter): void {
    this.navigate({ filter: filter === 'all' ? null : filter, page: null });
  }

  goToPage(page: number): void {
    this.navigate({ page: page > 0 ? page + 1 : null });
  }

  retry(): void {
    this.retry$.next();
  }

  onRemoved(task: AdminTask, removed: TaskDetail): void {
    this.removing.set(null);
    this.rows.update((rows) => rows?.map((row) => (row.id === task.id ? { ...row, status: removed.status } : row)) ?? rows);
    this.toastService.success(t('admin.taskRemoved', { name: task.clientName }));
    this.changed.emit();
  }

  private navigate(queryParams: Record<string, string | number | null>, replaceUrl = false): void {
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl });
  }
}
