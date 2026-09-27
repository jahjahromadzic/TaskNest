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
  LoaderCircle,
  LogIn,
  MapPin,
  RotateCcw,
  SearchX,
  Send,
  UserRound,
} from 'lucide';
import { Icon } from '../../components/icon/icon';
import { TaskDetail } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { CurrentUser } from '../../auth/current-user';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { TaskOffers } from '../../components/task-offers/task-offers';
import { TaskTimeline } from '../../components/task-timeline/task-timeline';
import { TaskService } from '../../services/task.service';
import { readApiError } from '../../shared/api-error';
import { daysLeft, formatBudget, formatDate, timeAgo } from '../../shared/format/format';
import { isStopped } from '../../shared/task-status/task-status';
import { ToastService } from '../../shared/toast/toast.service';

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
    TaskOffers,
    TaskTimeline,
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
    LoaderCircle,
    LogIn,
    MapPin,
    RotateCcw,
    SearchX,
    Send,
    UserRound,
  };

  readonly state$: Observable<DetailState>;
  readonly user$: Observable<CurrentUser | null>;

  readonly formatBudget = formatBudget;
  readonly formatDate = formatDate;
  readonly timeAgo = timeAgo;
  readonly daysLeft = daysLeft;
  readonly isStopped = isStopped;

  readonly publishing = signal(false);

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

  isOwner(task: TaskDetail, user: CurrentUser | null): boolean {
    return user !== null && user.id === task.clientId;
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
      this.toastService.success('Link copied. Paste it anywhere to share this task.');
    } catch {
      this.toastService.error('The link could not be copied. Copy it from the address bar instead.');
    }
  }

  publish(task: TaskDetail): void {
    if (this.publishing()) {
      return;
    }
    this.publishing.set(true);
    this.taskService.publishTask(task.id!).subscribe({
      next: () => {
        this.publishing.set(false);
        this.toastService.success('Your task is live. Taskers nearby can now send offers.');
        this.retry$.next();
      },
      error: (error) => {
        this.publishing.set(false);
        this.toastService.error(readApiError(error).message);
      },
    });
  }

  retry(): void {
    this.retry$.next();
  }
}

function isMissing(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 404 || error.status === 400);
}
