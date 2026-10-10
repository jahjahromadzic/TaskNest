import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AppNotification, TaskDetail } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { RealtimeService } from '../../services/realtime.service';

describe('Task details page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let live: { notifications$: Subject<AppNotification>; connected$: Subject<void> };

  const task: TaskDetail = {
    id: 't1',
    title: 'Fix the kitchen sink',
    description: 'The tap drips all night.',
    budget: 80,
    status: 'PUBLISHED',
    categoryId: 'c1',
    categoryName: 'Plumbing',
    municipalityName: 'Centar Sarajevo',
    clientId: 'owner-1',
    clientName: 'Amra Hodžić',
    publishedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 10 * 86_400_000).toISOString(),
  };

  beforeAll(async () => {
    await import('./task-detail');
  }, 60_000);

  beforeEach(async () => {
    live = { notifications$: new Subject(), connected$: new Subject() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: live },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(response: TaskDetail | { status: number }): Promise<void> {
    await harness.navigateByUrl('/tasks/t1');
    const request = http.expectOne('/api/tasks/t1');
    if ('status' in response && typeof response.status === 'number') {
      request.flush({ detail: 'Task not found' }, { status: response.status, statusText: 'Error' });
    } else {
      request.flush(response);
    }
    await harness.fixture.whenStable();
  }

  function logInAs(userId: string): void {
    TestBed.inject(AuthService).login({ email: 'x@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'token',
      expiresIn: 900,
      userId,
      email: 'x@test.ba',
      fullName: 'Some One',
      roles: ['CLIENT'],
    });
  }

  function text(): string {
    return harness.routeNativeElement?.textContent ?? '';
  }

  it('shows the task and names the tab after it', async () => {
    await open(task);

    expect(text()).toContain('Fix the kitchen sink');
    expect(text()).toContain('The tap drips all night.');
    expect(text()).toContain('80 KM');
    expect(text()).toContain('Amra Hodžić');
    expect(text()).toContain('Open for offers');
    expect(TestBed.inject(Title).getTitle()).toBe('Fix the kitchen sink · TaskNest');
  });

  it('shows the street next to the municipality to someone the server shares it with', async () => {
    logInAs('owner-1');

    await open({ ...task, addressLine: 'Zmaja od Bosne 12', latitude: 43.854947, longitude: 18.393707 });
    http.expectOne('/api/tasks/t1/offers').flush([]);
    await harness.fixture.whenStable();

    expect(location()).toBe('Zmaja od Bosne 12, Centar Sarajevo');
    expect(harness.routeNativeElement!.querySelector('app-map-view .leaflet-container')).not.toBeNull();
    expect(harness.routeNativeElement!.querySelectorAll('app-map-view .map-pin')).toHaveLength(1);
    const directions = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href*="google.com/maps"]')!;
    expect(directions.textContent).toContain('Open in Google Maps');
    expect(directions.href).toBe('https://www.google.com/maps/dir/?api=1&destination=43.854947,18.393707');
  });

  it('shows only the municipality when the server keeps the street private', async () => {
    await open(task);

    expect(location()).toBe('Centar Sarajevo');
    expect(text()).not.toContain('Zmaja od Bosne');
    expect(harness.routeNativeElement!.querySelector('app-map-view')).toBeNull();
    expect(text()).not.toContain('Open in Google Maps');
  });

  function location(): string {
    const element = harness.routeNativeElement!.querySelector('#task-location')!;
    return element.textContent!.replace(/\s+/g, ' ').trim();
  }

  it('invites a visitor to log in and come back to this task', async () => {
    await open(task);

    const login = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href^="/login"]')!;
    expect(login.textContent).toContain('Log in to make an offer');
    expect(decodeURIComponent(login.getAttribute('href')!)).toBe('/login?returnUrl=/tasks/t1');
  });

  it('tells the owner that the task is theirs and shows them the offers', async () => {
    logInAs('owner-1');

    await open(task);
    http.expectOne('/api/tasks/t1/offers').flush([{ id: 'o1', price: 70, status: 'PENDING', taskerName: 'Emir K' }]);
    await harness.fixture.whenStable();

    expect(text()).toContain('You posted this task');
    expect(text()).not.toContain('Log in to make an offer');
    expect(text()).toContain('Emir K');
  });

  it('does not ask for the offers when someone else looks at the task', async () => {
    await open(task);

    http.expectNone('/api/tasks/t1/offers');
  });

  it('does not treat another logged in user as the owner', async () => {
    logInAs('someone-else');

    await open(task);

    expect(text()).not.toContain('You posted this task');
  });

  it('lets another signed-in user report the task', async () => {
    logInAs('someone-else');
    await open(task);
    expect(text()).toContain('Report this task');

    Array.from<HTMLButtonElement>(harness.routeNativeElement!.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Report this task'))!
      .click();
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement!.querySelector('app-report-dialog dialog')!.textContent).toContain('What is wrong?');
  });

  it('does not offer the report link to a visitor', async () => {
    await open(task);

    expect(text()).not.toContain('Report this task');
  });

  it('does not offer the report link to the owner', async () => {
    logInAs('owner-1');

    await open(task);
    http.expectOne('/api/tasks/t1/offers').flush([]);
    await harness.fixture.whenStable();

    expect(text()).not.toContain('Report this task');
  });

  it('shows the close button as soon as the tasker reports the job done, without a reload', async () => {
    logInAs('owner-1');
    const working: TaskDetail = { ...task, status: 'IN_PROGRESS', assignedTaskerId: 'tasker-1', assignedTaskerName: 'Emir K' };
    await open(working);
    http.expectOne('/api/tasks/t1/offers').flush([]);
    await harness.fixture.whenStable();
    expect(text()).not.toContain('Confirm and close');

    live.notifications$.next({ type: 'TASK_COMPLETED', relatedEntityId: 'other-task' });
    http.expectNone('/api/tasks/t1');

    live.notifications$.next({ type: 'TASK_COMPLETED', relatedEntityId: 't1' });
    http.expectOne('/api/tasks/t1').flush({ ...working, status: 'COMPLETED', completedAt: new Date().toISOString() });
    await harness.fixture.whenStable();
    http.expectOne('/api/tasks/t1/offers').flush([]);
    await harness.fixture.whenStable();

    expect(text()).toContain('Confirm and close');
    expect(harness.routeNativeElement!.querySelector('.skeleton')).toBeNull();
  });

  it('explains that a cancelled task no longer takes offers', async () => {
    await open({ ...task, status: 'CANCELLED' });

    expect(text()).toContain('This task was cancelled');
    expect(harness.routeNativeElement!.querySelector('app-task-timeline')).toBeNull();
  });

  it('shows a friendly page when the task does not exist', async () => {
    await open({ status: 404 });

    expect(text()).toContain('This task is not available');
  });
});
