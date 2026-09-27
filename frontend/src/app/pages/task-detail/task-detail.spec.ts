import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { TaskDetail } from '../../api/models';
import { AuthService } from '../../auth/auth.service';

describe('Task details page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

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
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
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
