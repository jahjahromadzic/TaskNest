import { Component, ElementRef, ViewChild } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import { CircleAlert, ClipboardList, Plus, RotateCcw } from 'lucide';
import { TaskPage } from '../../api/models';
import { Icon } from '../../components/icon/icon';
import { Pagination } from '../../components/pagination/pagination';
import { TaskCard } from '../../components/task-card/task-card';
import { TaskService } from '../../services/task.service';
import { TaskStatus } from '../../shared/task-status/task-status';
import { translated } from '../../i18n/translate';
import { TranslatePipe } from '../../i18n/translate.pipe';

export interface MyTasksTab {
  key: string;
  readonly label: string;
  statuses: TaskStatus[];
  readonly empty: string;
}

export const MY_TASKS_TABS: MyTasksTab[] = [
  translated({ key: 'all', statuses: [] as TaskStatus[] }, { label: 'myTasks.tabAll', empty: 'myTasks.emptyAll' }),
  translated({ key: 'open', statuses: ['PUBLISHED'] as TaskStatus[] }, { label: 'myTasks.tabOpen', empty: 'myTasks.emptyOpen' }),
  translated(
    { key: 'active', statuses: ['ASSIGNED', 'IN_PROGRESS', 'COMPLETED'] as TaskStatus[] },
    { label: 'myTasks.tabActive', empty: 'myTasks.emptyActive' },
  ),
  translated({ key: 'drafts', statuses: ['DRAFT'] as TaskStatus[] }, { label: 'myTasks.tabDrafts', empty: 'myTasks.emptyDrafts' }),
  translated(
    { key: 'history', statuses: ['CLOSED', 'CANCELLED', 'EXPIRED', 'REMOVED'] as TaskStatus[] },
    { label: 'myTasks.tabHistory', empty: 'myTasks.emptyHistory' },
  ),
];

interface MyTasksQuery {
  tab: MyTasksTab;
  page: number;
}

interface ListState {
  loading: boolean;
  failed: boolean;
  result: TaskPage | null;
}

const LOADING: ListState = { loading: true, failed: false, result: null };
const FAILED: ListState = { loading: false, failed: true, result: null };

export function readQuery(params: ParamMap): MyTasksQuery {
  const tab = MY_TASKS_TABS.find((candidate) => candidate.key === params.get('tab')) ?? MY_TASKS_TABS[0];
  const page = Number(params.get('page'));
  return { tab, page: Number.isInteger(page) && page > 1 ? page - 1 : 0 };
}

export function countFor(tab: MyTasksTab, counts: Partial<Record<TaskStatus, number>> | null): number | null {
  if (!counts) {
    return null;
  }
  const statuses = tab.statuses.length > 0 ? tab.statuses : (Object.keys(counts) as TaskStatus[]);
  return statuses.reduce((sum, status) => sum + (counts[status] ?? 0), 0);
}

@Component({
  selector: 'app-my-tasks',
  imports: [AsyncPipe, RouterLink, Icon, Pagination, TaskCard, TranslatePipe],
  templateUrl: './my-tasks.html',
})
export class MyTasks {
  protected readonly icons = { CircleAlert, ClipboardList, Plus, RotateCcw };

  readonly tabs = MY_TASKS_TABS;
  readonly skeletonCards = [1, 2, 3];
  readonly countFor = countFor;

  readonly query$: Observable<MyTasksQuery>;
  readonly counts$: Observable<Partial<Record<TaskStatus, number>> | null>;
  readonly state$: Observable<ListState>;

  private readonly retry$ = new BehaviorSubject<void>(undefined);

  @ViewChild('list') private list?: ElementRef<HTMLElement>;

  constructor(
    private taskService: TaskService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.query$ = this.route.queryParamMap.pipe(map(readQuery), shareReplay(1));
    this.counts$ = this.retry$.pipe(
      switchMap(() => this.taskService.getMyTaskCounts().pipe(catchError(() => of(null)))),
      shareReplay(1),
    );
    this.state$ = combineLatest([this.query$, this.retry$]).pipe(
      switchMap(([query]) =>
        this.taskService.getMyTasks(query.tab.statuses, query.page).pipe(
          map((result): ListState => ({ loading: false, failed: false, result })),
          startWith(LOADING),
          catchError(() => of(FAILED)),
        ),
      ),
    );
  }

  selectTab(tab: MyTasksTab): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab: tab.key === 'all' ? null : tab.key } });
    this.scrollListToTop();
  }

  goToPage(page: number): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page > 0 ? page + 1 : null },
      queryParamsHandling: 'merge',
    });
    this.scrollListToTop();
  }

  retry(): void {
    this.retry$.next();
  }

  private scrollListToTop(): void {
    this.list?.nativeElement.scrollTo?.({ top: 0, behavior: 'smooth' });
    if (window.scrollY > 0) {
      window.scrollTo?.({ top: 0, behavior: 'smooth' });
    }
  }
}
