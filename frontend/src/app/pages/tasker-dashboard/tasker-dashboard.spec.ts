import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { Offer, TaskSummary, TaskerProfile } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { readDashboardQuery, sortOffersNewestFirst } from './tasker-dashboard';

describe('Tasker dashboard', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  const profile: TaskerProfile = {
    fullName: 'Emir Kovačević',
    averageRating: 4,
    completedJobsCount: 1,
    categories: [{ id: 'c1', name: 'Plumbing' }],
    municipalities: [{ id: 'm1', name: 'Centar' }],
  };
  const tap: TaskSummary = { id: 't1', title: 'Leaking tap', status: 'PUBLISHED', categoryName: 'Plumbing' };
  const boiler: TaskSummary = { id: 't2', title: 'Replace the boiler', status: 'PUBLISHED', categoryName: 'Plumbing' };
  const offers: Offer[] = [
    { id: 'o1', taskId: 't1', taskTitle: 'Leaking tap', price: 55, status: 'PENDING', createdAt: '2026-09-26T10:00:00' },
    { id: 'o2', taskId: 't9', taskTitle: 'Old job', price: 70, status: 'ACCEPTED', createdAt: '2026-09-01T10:00:00' },
  ];

  beforeAll(async () => {
    await import('./tasker-dashboard');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: 'emir@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'token',
      expiresIn: 900,
      userId: 'u1',
      email: 'emir@test.ba',
      fullName: 'Emir Kovačević',
      roles: ['CLIENT', 'TASKER'],
    });
    harness = await RouterTestingHarness.create();
  });

  function page(content: TaskSummary[], totalElements = content.length) {
    return { content, page: 0, size: 10, totalElements, totalPages: 1 };
  }

  function matching(size: number): TestRequest {
    return http.expectOne((request) => request.url === '/api/tasks/matching' && request.params.get('size') === String(size));
  }

  function assigned(size: number): TestRequest {
    return http.expectOne((request) => request.url === '/api/tasks/assigned' && request.params.get('size') === String(size));
  }

  async function open(url: string, withProfile = profile): Promise<void> {
    await harness.navigateByUrl(url);
    http.expectOne('/api/tasker-profiles/me').flush(withProfile);
    http.expectOne('/api/offers/mine').flush(offers);
    matching(1).flush(page([], 12));
    assigned(1).flush(page([], 2));
    await harness.fixture.whenStable();
  }

  function text(): string {
    return (harness.routeNativeElement?.textContent ?? '').replace(/\s+/g, ' ');
  }

  it('shows the counts of every tab and marks tasks the tasker already made an offer on', async () => {
    await open('/tasker');
    matching(10).flush(page([tap, boiler], 12));
    await harness.fixture.whenStable();

    const tabs = Array.from(harness.routeNativeElement!.querySelectorAll('[role=tab]')).map((tab) =>
      tab.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(tabs).toEqual(['Tasks for me 12', 'My offers 1', 'My jobs 2']);
    const cards = Array.from<HTMLElement>(harness.routeNativeElement!.querySelectorAll('app-task-card'));
    expect(cards[0].textContent).toContain('Offer sent · 55 KM');
    expect(cards[1].textContent).not.toContain('Offer sent');
  });

  it('lists the offers from the offers already loaded, hired ones first', async () => {
    await open('/tasker?tab=offers');

    const rows = Array.from<HTMLElement>(harness.routeNativeElement!.querySelectorAll('a[href^="/tasks/"]')).map(
      (row) => row.textContent!.replace(/\s+/g, ' '),
    );
    expect(rows[0]).toContain('Hired');
    expect(rows[1]).toContain('Waiting for the client');
    http.expectNone((request) => request.url === '/api/tasks/matching' && request.params.get('size') === '10');
  });

  it('opens the jobs tab from the address and asks for the assigned tasks', async () => {
    await open('/tasker?tab=jobs');

    assigned(10).flush(page([{ ...tap, status: 'ASSIGNED' }]));
    await harness.fixture.whenStable();

    expect(text()).toContain('Assigned');
  });

  it('warns a tasker whose profile cannot match anything yet', async () => {
    await open('/tasker', { ...profile, categories: [] });
    matching(10).flush(page([]));
    await harness.fixture.whenStable();

    expect(text()).toContain('Choose at least one category and one municipality');
    expect(text()).toContain('No matching tasks right now');
  });

  it('puts the chosen tab in the address', async () => {
    await open('/tasker');
    matching(10).flush(page([]));
    await harness.fixture.whenStable();

    Array.from<HTMLButtonElement>(harness.routeNativeElement!.querySelectorAll('[role=tab]'))
      .find((tab) => tab.textContent!.includes('My jobs'))!
      .click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/tasker?tab=jobs');
    assigned(10).flush(page([]));
  });
});

describe('Tasker dashboard helpers', () => {
  it('falls back to the first tab for unknown values', () => {
    expect(readDashboardQuery(convertToParamMap({ tab: 'hack', page: '3' }))).toEqual({ tab: 'matching', page: 2 });
  });

  it('puts hired offers first, then waiting ones, then the rest, newest first', () => {
    const sorted = sortOffersNewestFirst([
      { id: 'old-pending', status: 'PENDING', createdAt: '2026-09-01' },
      { id: 'withdrawn', status: 'WITHDRAWN', createdAt: '2026-09-25' },
      { id: 'new-pending', status: 'PENDING', createdAt: '2026-09-20' },
      { id: 'hired', status: 'ACCEPTED', createdAt: '2026-08-01' },
    ]);

    expect(sorted.map((offer) => offer.id)).toEqual(['hired', 'new-pending', 'old-pending', 'withdrawn']);
  });
});
