import { Injectable, Signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, map, merge, of, tap } from 'rxjs';
import { AppNotification, NotificationPage } from '../api/models';
import { AuthService } from '../auth/auth.service';
import { notificationLink } from '../shared/notification-kind/notification-kind';
import { UnreadCounter } from '../shared/unread-counter/unread-counter';
import { RealtimeService } from './realtime.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly unreadCount: Signal<number>;

  private readonly counter: UnreadCounter;

  constructor(
    private http: HttpClient,
    private router: Router,
    authService: AuthService,
    realtime: RealtimeService,
  ) {
    this.counter = new UnreadCounter(
      http,
      authService.user$,
      '/api/notifications/unread-count',
      merge(realtime.connected$, realtime.notifications$),
    );
    this.unreadCount = this.counter.count.asReadonly();
  }

  recount(): void {
    this.counter.recount();
  }

  list(page: number, size = 20): Observable<NotificationPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<NotificationPage>('/api/notifications', { params });
  }

  markRead(notification: AppNotification): Observable<void> {
    if (notification.read || !notification.id) {
      return of(undefined);
    }
    this.counter.lower();
    return this.http.post<AppNotification>(`/api/notifications/${encodeURIComponent(notification.id)}/read`, null).pipe(
      map(() => undefined),
      tap({ finalize: () => this.counter.recount() }),
    );
  }

  markAllRead(): Observable<void> {
    return this.http.post<{ marked: number }>('/api/notifications/read-all', null).pipe(
      tap(() => this.counter.count.set(0)),
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
