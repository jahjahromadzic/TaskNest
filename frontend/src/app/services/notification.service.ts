import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  fromEvent,
  map,
  merge,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { AppNotification, NotificationPage } from '../api/models';
import { AuthService } from '../auth/auth.service';
import { notificationLink } from '../shared/notification-kind/notification-kind';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly unreadCount = signal(0);

  private readonly recount$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private router: Router,
    authService: AuthService,
  ) {
    const userId$ = authService.user$.pipe(
      map((user) => user?.id ?? null),
      distinctUntilChanged(),
    );
    const checkAgain$ = merge(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
      fromEvent(document, 'visibilitychange').pipe(filter(() => document.visibilityState === 'visible')),
      this.recount$,
    ).pipe(startWith(null));

    combineLatest([userId$, checkAgain$])
      .pipe(
        switchMap(([userId]) =>
          userId
            ? this.http.get<{ count: number }>('/api/notifications/unread-count').pipe(
                map((response) => response.count),
                catchError(() => EMPTY),
              )
            : of(0),
        ),
      )
      .subscribe((count) => this.unreadCount.set(count));
  }

  list(page: number, size = 20): Observable<NotificationPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<NotificationPage>('/api/notifications', { params });
  }

  markRead(notification: AppNotification): Observable<void> {
    if (notification.read || !notification.id) {
      return of(undefined);
    }
    this.unreadCount.update((count) => Math.max(0, count - 1));
    return this.http.post<AppNotification>(`/api/notifications/${encodeURIComponent(notification.id)}/read`, null).pipe(
      map(() => undefined),
      tap({ finalize: () => this.recount$.next() }),
    );
  }

  markAllRead(): Observable<void> {
    return this.http.post<{ marked: number }>('/api/notifications/read-all', null).pipe(
      tap(() => this.unreadCount.set(0)),
      map(() => undefined),
    );
  }

  open(notification: AppNotification): void {
    this.markRead(notification).subscribe({ error: () => undefined });
    const link = notificationLink(notification);
    if (link) {
      this.router.navigateByUrl(link);
    }
  }
}
