import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { ToastService } from '../../shared/toast/toast.service';

describe('Post a task page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  const draft = { id: 't1', title: 'Fix the sink', status: 'DRAFT' };

  beforeAll(async () => {
    await import('./post-task');
    await import('../task-detail/task-detail');
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
    await harness.navigateByUrl('/tasks/new');
    http.expectOne('/api/categories').flush([{ id: 'c1', name: 'Plumbing' }]);
    http.expectOne('/api/municipalities').flush([{ id: 'm1', name: 'Centar Sarajevo' }]);
    await harness.fixture.whenStable();
  });

  function page(): HTMLElement {
    return harness.routeNativeElement!;
  }

  function text(): string {
    return page().textContent ?? '';
  }

  async function fillValidForm(): Promise<void> {
    const title = page().querySelector<HTMLInputElement>('#task-title')!;
    title.value = 'Fix the sink';
    title.dispatchEvent(new Event('input'));
    const budget = page().querySelector<HTMLInputElement>('#task-budget')!;
    budget.value = '60';
    budget.dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('.category-tile')!.click();
    page().querySelector<HTMLButtonElement>('#task-municipality')!.click();
    await harness.fixture.whenStable();
    page().querySelector<HTMLElement>('[role=option]')!.click();
    await harness.fixture.whenStable();
  }

  async function click(label: string): Promise<void> {
    Array.from(page().querySelectorAll<HTMLButtonElement>('form button'))
      .find((button) => button.textContent?.includes(label))!
      .click();
    await harness.fixture.whenStable();
  }

  it('lists what is missing and sends nothing when the form is empty', async () => {
    await click('Publish task');

    expect(text()).toContain('Give your task a short title');
    expect(text()).toContain('Choose the kind of work');
    expect(text()).toContain('Choose a municipality');
    http.expectNone('/api/tasks');
  });

  it('shows the task in the preview while it is being written', async () => {
    await fillValidForm();

    const preview = page().querySelector('aside app-task-card')!.textContent;
    expect(preview).toContain('Fix the sink');
    expect(preview).toContain('Plumbing');
    expect(preview).toContain('60 KM');
  });

  it('creates and then publishes the task and opens it', async () => {
    await fillValidForm();
    await click('Publish task');

    const create = http.expectOne({ method: 'POST', url: '/api/tasks' });
    expect(create.request.body).toEqual({
      title: 'Fix the sink',
      description: undefined,
      categoryId: 'c1',
      municipalityId: 'm1',
      budget: 60,
    });
    create.flush(draft);
    http.expectOne({ method: 'POST', url: '/api/tasks/t1/publish' }).flush({ ...draft, status: 'PUBLISHED' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/tasks/t1');
    expect(TestBed.inject(ToastService).toasts()[0].text).toContain('Your task is live');
    http.expectOne('/api/tasks/t1').flush({ ...draft, status: 'PUBLISHED' });
  });

  it('only creates a draft when the client saves it for later', async () => {
    await fillValidForm();
    await click('Save as draft');

    http.expectOne({ method: 'POST', url: '/api/tasks' }).flush(draft);
    await harness.fixture.whenStable();

    http.expectNone('/api/tasks/t1/publish');
    expect(TestBed.inject(ToastService).toasts()[0].text).toContain('Draft saved');
    http.expectOne('/api/tasks/t1').flush(draft);
  });

  it('keeps the draft and says so when publishing fails', async () => {
    await fillValidForm();
    await click('Publish task');

    http.expectOne({ method: 'POST', url: '/api/tasks' }).flush(draft);
    http
      .expectOne('/api/tasks/t1/publish')
      .flush({ detail: 'Try again later' }, { status: 503, statusText: 'Unavailable' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/tasks/t1');
    expect(TestBed.inject(ToastService).toasts()[0].text).toContain('saved as a draft');
    http.expectOne('/api/tasks/t1').flush(draft);
  });

  it('shows the server message under the field it belongs to', async () => {
    await fillValidForm();
    await click('Publish task');

    http
      .expectOne({ method: 'POST', url: '/api/tasks' })
      .flush({ detail: 'title: size must be between 0 and 200' }, { status: 400, statusText: 'Bad Request' });
    await harness.fixture.whenStable();

    expect(text()).toContain('Size must be between 0 and 200');
    expect(TestBed.inject(Router).url).toBe('/tasks/new');
  });
});
