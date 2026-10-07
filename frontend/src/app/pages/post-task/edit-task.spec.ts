import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { TaskDetail } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { currentLang } from '../../i18n/lang';
import { ToastService } from '../../shared/toast/toast.service';

describe('Edit a task page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  const open: TaskDetail = {
    id: 't1',
    title: 'Fix the sink',
    description: 'It drips all night',
    status: 'PUBLISHED',
    categoryId: 'c1',
    municipalityId: 'm1',
    budget: 60,
    clientId: 'u1',
    publishedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 20 * 86_400_000).toISOString(),
    photos: [{ id: 'p1', url: '/api/photos/p1' }],
  };

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
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  async function edit(task: TaskDetail): Promise<void> {
    await harness.navigateByUrl('/tasks/t1/edit');
    http.expectOne('/api/categories').flush([
      { id: 'c1', name: 'Plumbing' },
      { id: 'c2', name: 'Cleaning' },
    ]);
    http.expectOne('/api/municipalities').flush([{ id: 'm1', name: 'Centar Sarajevo' }]);
    http.expectOne({ method: 'GET', url: '/api/tasks/t1' }).flush(task);
    await harness.fixture.whenStable();
  }

  function page(): HTMLElement {
    return harness.routeNativeElement!;
  }

  function text(): string {
    return (page().textContent ?? '').replace(/\s+/g, ' ');
  }

  async function type(selector: string, value: string): Promise<void> {
    const input = page().querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
  }

  async function click(label: string): Promise<void> {
    Array.from(page().querySelectorAll<HTMLButtonElement>('form button'))
      .find((button) => button.textContent?.includes(label))!
      .click();
    await harness.fixture.whenStable();
  }

  it('fills the form with the task and saves only the changes the client made', async () => {
    await edit(open);

    expect(text()).toContain('Edit task');
    expect(text()).toContain('Taskers who already sent an offer will be told about the change.');
    expect(page().querySelector<HTMLInputElement>('#task-title')!.value).toBe('Fix the sink');
    expect(page().querySelector<HTMLTextAreaElement>('#task-description')!.value).toBe('It drips all night');
    expect(page().querySelector('aside app-task-card img')?.getAttribute('src')).toBe('/api/photos/p1');
    expect(text()).toContain('Photos (1/5) are added and removed on the task page.');

    await type('#task-title', 'Fix the sink and the tap');
    await type('#task-budget', '90');
    page().querySelectorAll<HTMLButtonElement>('.category-tile')[1].click();
    await click('Save changes');

    const save = http.expectOne({ method: 'PUT', url: '/api/tasks/t1' });
    expect(save.request.body).toEqual({
      title: 'Fix the sink and the tap',
      description: 'It drips all night',
      categoryId: 'c2',
      municipalityId: 'm1',
      budget: 90,
    });
    save.flush({ ...open, title: 'Fix the sink and the tap' });
    await harness.fixture.whenStable();

    http.expectNone('/api/tasks/t1/publish');
    expect(TestBed.inject(Router).url).toBe('/tasks/t1');
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Your changes are saved.');
    http.expectOne('/api/tasks/t1').flush(open);
  });

  it('saves a draft and publishes it in one step', async () => {
    await edit({ ...open, status: 'DRAFT', publishedAt: undefined, expiresAt: undefined });

    await click('Save and publish');
    http.expectOne({ method: 'PUT', url: '/api/tasks/t1' }).flush({ ...open, status: 'DRAFT' });
    http.expectOne({ method: 'POST', url: '/api/tasks/t1/publish' }).flush(open);
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/tasks/t1');
    expect(TestBed.inject(ToastService).toasts()[0].text).toContain('Your task is live');
    http.expectOne('/api/tasks/t1').flush(open);
  });

  it('explains in Bosnian when the server refuses the change', async () => {
    currentLang.set('bs');
    await edit(open);

    await click('Sačuvaj izmjene');
    http
      .expectOne({ method: 'PUT', url: '/api/tasks/t1' })
      .flush(
        { status: 400, detail: 'Only a draft or a task that is open for offers can be edited' },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toContain(
      'Mijenjati se može samo nacrt ili oglas koji prima ponude',
    );
    expect(TestBed.inject(Router).url).toBe('/tasks/t1/edit');
  });

  it('does not open the form once a tasker is hired', async () => {
    await edit({ ...open, status: 'ASSIGNED' });

    expect(page().querySelector('form')).toBeNull();
    expect(text()).toContain('This task can no longer be edited');
  });

  it("does not open the form for someone else's task", async () => {
    await edit({ ...open, clientId: 'someone-else' });

    expect(page().querySelector('form')).toBeNull();
    expect(text()).toContain('This task does not exist or it is not yours.');
  });
});
