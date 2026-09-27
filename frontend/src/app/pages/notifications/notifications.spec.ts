import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AppNotification } from '../../api/models';
import { AuthService } from '../../auth/auth.service';

describe('Notifications page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  const today = new Date();
  const stamp = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}T00:00:01`;
  const offer: AppNotification = { id: 'n1', type: 'NEW_OFFER', content: 'Emir offered 55 KM', relatedEntityId: 't1', read: false, createdAt: stamp };
  const started: AppNotification = { id: 'n2', type: 'TASK_STARTED', content: 'Work has started', relatedEntityId: 't2', read: true, createdAt: '2025-01-10T10:00:00' };

  beforeAll(async () => {
    await import('./notifications');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
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
    harness = await RouterTestingHarness.create();
  });

  function list(page: number): TestRequest {
    return http.expectOne((request) => request.url === '/api/notifications' && request.params.get('page') === String(page));
  }

  function answerCounts(count: number): void {
    http
      .match('/api/notifications/unread-count')
      .filter((request) => !request.cancelled)
      .forEach((request) => request.flush({ count }));
  }

  function text(): string {
    return (harness.routeNativeElement?.textContent ?? '').replace(/\s+/g, ' ');
  }

  async function open(items: AppNotification[], total = items.length, unread = 1): Promise<void> {
    await harness.navigateByUrl('/notifications');
    list(0).flush({ content: items, page: 0, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) });
    answerCounts(unread);
    await harness.fixture.whenStable();
  }

  function button(label: string): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(harness.routeNativeElement!.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes(label),
    )!;
  }

  it('groups notifications by day and shows how many are unread', async () => {
    await open([offer, started]);

    const headings = Array.from(harness.routeNativeElement!.querySelectorAll('section h2')).map((heading) => heading.textContent);
    expect(headings).toEqual(['Today', 'Earlier']);
    expect(text()).toContain('1 unread');
    expect(text()).toContain('Work started');
    expect(button('Mark all as read').disabled).toBe(false);
  });

  it('loads older notifications without showing one twice', async () => {
    await open([offer], 21);

    button('Show older notifications').click();
    list(1).flush({ content: [offer, started], page: 1, size: 20, totalElements: 21, totalPages: 2 });
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement!.querySelectorAll('app-notification-item').length).toBe(2);
  });

  it('marks everything as read and disables the button', async () => {
    await open([offer, started]);

    button('Mark all as read').click();
    http.expectOne({ method: 'POST', url: '/api/notifications/read-all' }).flush({ marked: 1 });
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement!.querySelectorAll('[aria-label=Unread]').length).toBe(0);
    expect(text()).toContain('You are all caught up');
    expect(button('Mark all as read').disabled).toBe(true);
  });

  it('opens the task of a notification and shows it as read', async () => {
    await open([offer]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    harness.routeNativeElement!.querySelector<HTMLButtonElement>('app-notification-item button')!.click();
    await harness.fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/tasks/t1');
    expect(harness.routeNativeElement!.querySelectorAll('[aria-label=Unread]').length).toBe(0);
    http.expectOne('/api/notifications/n1/read').flush({ ...offer, read: true });
  });

  it('offers to try again when the list cannot be loaded', async () => {
    await harness.navigateByUrl('/notifications');
    list(0).flush(null, { status: 500, statusText: 'Server error' });
    answerCounts(0);
    await harness.fixture.whenStable();

    expect(text()).toContain('Something went wrong');
    button('Try again').click();
    list(0).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await harness.fixture.whenStable();

    expect(text()).toContain('No notifications yet');
  });
});
