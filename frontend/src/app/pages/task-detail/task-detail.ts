import { Component, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, map, of, startWith, switchMap, tap } from 'rxjs';
import {
  ArrowLeft,
  CalendarClock,
  ChevronRight,
  CircleAlert,
  FileText,
  Info,
  Link,
  LogIn,
  MapPin,
  RotateCcw,
  SearchX,
  ShieldAlert,
  UserRound,
} from 'lucide';
import { Icon } from '../../components/icon/icon';
import { TaskDetail } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { CurrentUser } from '../../auth/current-user';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { TaskActions } from '../../components/task-actions/task-actions';
import { TaskOffers } from '../../components/task-offers/task-offers';
import { TaskReviews } from '../../components/task-reviews/task-reviews';
import { TaskerPanel } from '../../components/tasker-panel/tasker-panel';
import { RemoveTaskDialog } from '../../components/remove-task-dialog/remove-task-dialog';
import { TaskTimeline } from '../../components/task-timeline/task-timeline';
import { TaskPhotos } from '../../components/task-photos/task-photos';
import { TaskService } from '../../services/task.service';
import { daysLeft, formatBudget, formatDate, timeAgo } from '../../shared/format/format';
import { isStopped } from '../../shared/task-status/task-status';
import { ToastService } from '../../shared/toast/toast.service';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

interface DetailState {
  loading: boolean;
  notFound: boolean;
  failed: boolean;
  task: TaskDetail | null;
}

const LOADING: DetailState = { loading: true, notFound: false, failed: false, task: null };
const NOT_FOUND: DetailState = { loading: false, notFound: true, failed: false, task: null };
const FAILED: DetailState = { loading: false, notFound: false, failed: true, task: null };

@Component({
  selector: 'app-task-detail',
  imports: [
    Icon,
    AsyncPipe,
    RouterLink,
    CategoryIcon,
    StatusBadge,
    TaskActions,
    TaskOffers,
    TaskReviews,
    TaskTimeline,
    TaskPhotos,
    TaskerPanel,
    RemoveTaskDialog,
    TranslatePipe,
    CategoryPipe,
  ],
  templateUrl: './task-detail.html',
})
export class TaskDetailPage {
  protected readonly icons = {
    ArrowLeft,
    CalendarClock,
    ChevronRight,
    CircleAlert,
    FileText,
    Info,
    Link,
    LogIn,
    MapPin,
    RotateCcw,
    SearchX,
    ShieldAlert,
    UserRound,
  };

  readonly state$: Observable<DetailState>;
  readonly user$: Observable<CurrentUser | null>;

  readonly formatBudget = formatBudget;
  readonly formatDate = formatDate;
  readonly timeAgo = timeAgo;
  readonly daysLeft = daysLeft;
  readonly isStopped = isStopped;

  readonly removing = signal(false);

  private readonly retry$ = new BehaviorSubject<void>(undefined);

  constructor(
    private taskService: TaskService,
    private authService: AuthService,
    private toastService: ToastService,
    private route: ActivatedRoute,
    private title: Title,
  ) {
    this.user$ = this.authService.user$;
    this.state$ = this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      switchMap((id) =>
        this.retry$.pipe(
          switchMap((_, attempt) => {
            const request$ = this.taskService.getTask(id).pipe(
              tap((task) => this.title.setTitle(`${task.title} · TaskNest`)),
              map((task): DetailState => ({ loading: false, notFound: false, failed: false, task })),
              catchError((error) => of(isMissing(error) ? NOT_FOUND : FAILED)),
            );
            return attempt === 0 ? request$.pipe(startWith(LOADING)) : request$;
          }),
        ),
      ),
    );
  }

  canEditPhotos(task: TaskDetail, user: CurrentUser | null): boolean {
    return this.isOwner(task, user) && (task.status === 'DRAFT' || task.status === 'PUBLISHED');
  }

  isOwner(task: TaskDetail, user: CurrentUser | null): boolean {
    return user !== null && user.id === task.clientId;
  }

  isAssignedTasker(task: TaskDetail, user: CurrentUser | null): boolean {
    return user !== null && user.id === task.assignedTaskerId;
  }

  initials(name: string | undefined): string {
    return (name ?? '')
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }

  taskUrl(task: TaskDetail): string {
    return `/tasks/${task.id}`;
  }

  async share(): Promise<void> {
    try {
      await navigator.clipboard.writeText(window.location.href);
      this.toastService.success(t('detail.linkCopied'));
    } catch {
      this.toastService.error(t('detail.linkFailed'));
    }
  }

  retry(): void {
    this.retry$.next();
  }

  canModerate(task: TaskDetail, user: CurrentUser | null): boolean {
    return !!user?.roles.includes('ADMIN') && (task.status === 'PUBLISHED' || task.status === 'ASSIGNED');
  }

  onRemoved(): void {
    this.removing.set(false);
    this.toastService.success(t('detail.removed'));
    this.retry();
  }
}

function isMissing(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 404 || error.status === 400);
}
