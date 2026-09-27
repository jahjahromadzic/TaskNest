import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { TaskSummary } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { MY_TASKS_TABS, countFor, readQuery } from './my-tasks';

describe('My tasks page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  const draft: TaskSummary = { id: 't1', title: 'Lay the laminate', status: 'DRAFT', categoryName: 'Furniture assembly' };

  beforeAll(async () => {
    await import('./my-tasks');
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

  afterEach(() => http.verify());

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url);
    http.expectOne('/api/tasks/mine/counts').flush({ DRAFT: 1, PUBLISHED: 6, ASSIGNED: 1, COMPLETED: 1, CLOSED: 2 });
  }

  function listRequest(): TestRequest {
    return http.expectOne((request) => request.url === '/api/tasks/mine');
  }

  function page(content: TaskSummary[]) {
    return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 };
  }

  function tabs(): string[] {
    return Array.from(harness.routeNativeElement!.querySelectorAll('[role=tab]')).map((tab) =>
      tab.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('adds up the counts of every status in a tab', async () => {
    await open('/my-tasks');
    listRequest().flush(page([draft]));
    await harness.fixture.whenStable();

    expect(tabs()).toEqual(['All 11', 'Open 6', 'In progress 2', 'Drafts 1', 'History 2']);
  });

  it('asks only for the statuses of the chosen tab and shows their status on each card', async () => {
    await open('/my-tasks?tab=drafts');

    const request = listRequest();
    expect(request.request.params.getAll('status')).toEqual(['DRAFT']);
    request.flush(page([draft]));
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement!.querySelector('app-task-card')!.textContent).toContain('Draft');
    expect(harness.routeNativeElement!.textContent).toContain('Not published yet');
  });

  it('puts the chosen tab in the address', async () => {
    await open('/my-tasks');
    listRequest().flush(page([]));
    await harness.fixture.whenStable();

    Array.from(harness.routeNativeElement!.querySelectorAll<HTMLButtonElement>('[role=tab]'))
      .find((tab) => tab.textContent!.includes('History'))!
      .click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/my-tasks?tab=history');
    expect(listRequest().request.params.getAll('status')).toEqual(['CLOSED', 'CANCELLED', 'EXPIRED', 'REMOVED']);
  });

  it('explains an empty tab and offers to post a task', async () => {
    await open('/my-tasks?tab=open');
    listRequest().flush(page([]));
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement!.textContent).toContain('None of your tasks is waiting for offers.');
  });
});

describe('My tasks helpers', () => {
  it('falls back to the first tab and page for unknown values', () => {
    expect(readQuery(convertToParamMap({ tab: 'hack', page: 'x' }))).toEqual({ tab: MY_TASKS_TABS[0], page: 0 });
  });

  it('shows no count until the counts have arrived', () => {
    expect(countFor(MY_TASKS_TABS[1], null)).toBeNull();
  });
});
