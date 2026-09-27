import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AppNotification } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { NotificationBell } from './notification-bell';

describe('Notification bell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let http: HttpTestingController;

  const offer: AppNotification = {
    id: 'n1',
    type: 'NEW_OFFER',
    content: 'Emir Kovačević offered 55 KM for: Leaking tap',
    relatedEntityId: 't1',
    read: false,
    createdAt: '2026-09-27T10:00:00',
  };
  const review: AppNotification = { id: 'n2', type: 'REVIEW_RECEIVED', content: 'You received a review', read: true };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'token',
      expiresIn: 900,
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles: ['CLIENT'],
    });
    fixture = TestBed.createComponent(NotificationBell);
    await fixture.whenStable();
  });

  function bell(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button[aria-haspopup=dialog]');
  }

  async function showCount(count: number): Promise<void> {
    http.expectOne('/api/notifications/unread-count').flush({ count });
    await fixture.whenStable();
  }

  async function open(items: AppNotification[]): Promise<void> {
    bell().click();
    await fixture.whenStable();
    const request = http.expectOne((req) => req.url === '/api/notifications');
    expect(request.request.params.get('size')).toBe('5');
    request.flush({ content: items, page: 0, size: 5, totalElements: items.length, totalPages: 1 });
    await fixture.whenStable();
  }

  it('shows the unread count on the bell and caps it at 9+', async () => {
    await showCount(3);
    expect(bell().textContent?.trim()).toBe('3');
    expect(bell().getAttribute('aria-label')).toBe('Notifications, 3 unread');

    TestBed.inject(AuthService).logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    await fixture.whenStable();
    expect(bell().textContent?.trim()).toBe('');
  });

  it('caps a large count', async () => {
    await showCount(27);
    expect(bell().textContent?.trim()).toBe('9+');
  });

  it('loads the latest notifications when opened and shows their meaning', async () => {
    await showCount(1);
    await open([offer, review]);

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('New offer');
    expect(text).toContain('Emir Kovačević offered 55 KM for: Leaking tap');
    expect(text).toContain('New review');
    expect(fixture.nativeElement.querySelectorAll('[aria-label=Unread]').length).toBe(1);
  });

  it('says so when there is nothing to show', async () => {
    await showCount(0);
    await open([]);

    expect(fixture.nativeElement.textContent).toContain('You are all caught up');
    expect(fixture.nativeElement.textContent).not.toContain('Mark all as read');
  });

  it('closes and opens the task when a notification is chosen', async () => {
    await showCount(1);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await open([offer]);

    fixture.nativeElement.querySelector('app-notification-item button').click();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/tasks/t1');
    http.expectOne('/api/notifications/n1/read').flush({ ...offer, read: true });
    expect(fixture.nativeElement.querySelector('[role=dialog]')).toBeNull();
  });

  it('marks everything as read from the dropdown', async () => {
    await showCount(1);
    await open([offer]);

    const markAll = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Mark all as read'),
    )!;
    markAll.click();
    http.expectOne({ method: 'POST', url: '/api/notifications/read-all' }).flush({ marked: 1 });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('[aria-label=Unread]').length).toBe(0);
    expect(bell().textContent?.trim()).toBe('');
  });

  it('closes on Escape', async () => {
    await showCount(0);
    await open([review]);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role=dialog]')).toBeNull();
  });
});
