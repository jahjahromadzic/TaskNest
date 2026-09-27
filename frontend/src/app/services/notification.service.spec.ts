import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AppNotification } from '../api/models';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from './notification.service';
import { RealtimeService } from './realtime.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let http: HttpTestingController;
  let authService: AuthService;
  let live: { connected$: Subject<void>; notifications$: Subject<AppNotification> };

  beforeEach(() => {
    live = { connected$: new Subject(), notifications$: new Subject() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: live },
      ],
    });
    service = TestBed.inject(NotificationService);
    http = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  function logIn(): void {
    authService.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'token',
      expiresIn: 900,
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles: ['CLIENT'],
    });
  }

  function answerCount(count: number): void {
    http.expectOne('/api/notifications/unread-count').flush({ count });
  }

  it('asks for the unread count only once someone is logged in', () => {
    expect(service.unreadCount()).toBe(0);
    http.expectNone('/api/notifications/unread-count');

    logIn();
    answerCount(3);

    expect(service.unreadCount()).toBe(3);
  });

  it('checks the count again when a notification arrives live or the connection comes back, and resets it on logout', async () => {
    logIn();
    answerCount(1);

    await TestBed.inject(Router).navigateByUrl('/tasks');
    http.expectNone('/api/notifications/unread-count');

    live.notifications$.next({ id: 'n9', type: 'NEW_OFFER', read: false });
    answerCount(2);
    expect(service.unreadCount()).toBe(2);

    live.connected$.next();
    answerCount(4);
    expect(service.unreadCount()).toBe(4);

    authService.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    expect(service.unreadCount()).toBe(0);
  });

  it('lowers the count right away when a notification is read, then asks the server again', () => {
    logIn();
    answerCount(2);

    service.markRead({ id: 'n1', read: false }).subscribe();
    expect(service.unreadCount()).toBe(1);

    http.expectOne({ method: 'POST', url: '/api/notifications/n1/read' }).flush({ id: 'n1', read: true });
    answerCount(1);
    expect(service.unreadCount()).toBe(1);
  });

  it('does not call the server for a notification that is already read', () => {
    logIn();
    answerCount(2);

    service.markRead({ id: 'n1', read: true }).subscribe();

    expect(service.unreadCount()).toBe(2);
  });

  it('clears the count after marking everything as read', () => {
    logIn();
    answerCount(5);

    service.markAllRead().subscribe();
    http.expectOne({ method: 'POST', url: '/api/notifications/read-all' }).flush({ marked: 5 });

    expect(service.unreadCount()).toBe(0);
  });

  it('opens the related page and marks the notification as read', () => {
    logIn();
    answerCount(1);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    service.open({ id: 'n1', type: 'NEW_OFFER', relatedEntityId: 't1', read: false });
    http.expectOne('/api/notifications/n1/read').flush({ id: 'n1', read: true });
    answerCount(0);

    expect(navigate).toHaveBeenCalledWith('/tasks/t1');
    expect(service.unreadCount()).toBe(0);
  });
});
