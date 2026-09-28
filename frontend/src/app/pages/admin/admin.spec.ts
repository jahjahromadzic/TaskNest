import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AdminStats, AdminTask, AdminUser } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { ConfirmOptions, ConfirmService } from '../../shared/confirm/confirm.service';
import { readTaskQuery, readUserQuery } from './admin-query';

describe('Admin panel', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let answer: boolean;
  let asked: ConfirmOptions[];

  const stats: AdminStats = { users: 38, suspendedUsers: 2, taskers: 11, unverifiedTaskers: 9, openTasks: 27, removedTasks: 0 };
  const me: AdminUser = { id: 'admin', fullName: 'Lejla Mujić', email: 'lejla@test.ba', roles: ['CLIENT', 'ADMIN'], accountStatus: 'ACTIVE' };
  const tasker: AdminUser = {
    id: 'u2',
    fullName: 'Tarik Hasanović',
    email: 'tarik@test.ba',
    roles: ['CLIENT', 'TASKER'],
    accountStatus: 'ACTIVE',
    taskerProfileId: 'p2',
    taskerVerified: false,
  };
  const suspended: AdminUser = { id: 'u3', fullName: 'Haris Mehić', email: 'haris@test.ba', roles: ['CLIENT'], accountStatus: 'SUSPENDED' };
  const openTask: AdminTask = {
    id: 't1',
    title: 'Leaking tap',
    status: 'PUBLISHED',
    categorySlug: 'plumbing',
    clientName: 'Amra Hodžić',
    clientEmail: 'amra@test.ba',
    municipalityName: 'Centar',
    budget: 50,
  };
  const closedTask: AdminTask = { ...openTask, id: 't2', title: 'Old job', status: 'CLOSED' };

  beforeAll(async () => {
    await import('./admin');
  }, 60_000);

  beforeEach(async () => {
    answer = true;
    asked = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ConfirmService,
          useValue: {
            ask: (options: ConfirmOptions) => {
              asked.push(options);
              return Promise.resolve(answer);
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: 'lejla@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'token',
      expiresIn: 900,
      userId: 'admin',
      email: 'lejla@test.ba',
      fullName: 'Lejla Mujić',
      roles: ['CLIENT', 'ADMIN'],
    });
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  function page<T>(content: T[]) {
    return { content, page: 0, size: 15, totalElements: content.length, totalPages: 1 };
  }

  function users(): TestRequest {
    return http.expectOne((request) => request.url === '/api/admin/users');
  }

  function tasks(): TestRequest {
    return http.expectOne((request) => request.url === '/api/admin/tasks');
  }

  async function open(url = '/admin', list: AdminUser[] = [me, tasker, suspended]): Promise<void> {
    await harness.navigateByUrl(url);
    http.expectOne('/api/admin/stats').flush(stats);
    if (url.includes('tab=tasks')) {
      tasks().flush(page([openTask, closedTask]));
    } else {
      users().flush(page(list));
    }
    await harness.fixture.whenStable();
  }

  function element(): HTMLElement {
    return harness.routeNativeElement!;
  }

  function row(name: string): HTMLElement {
    return Array.from<HTMLElement>(element().querySelectorAll('article')).find((article) => article.textContent!.includes(name))!;
  }

  function button(container: HTMLElement, label: string): HTMLButtonElement | undefined {
    return Array.from<HTMLButtonElement>(container.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes(label),
    );
  }

  it('shows the overview and offers actions only where they are allowed', async () => {
    await open();

    expect(element().textContent).toContain('38');
    expect(element().textContent).toContain('9 not verified yet');
    expect(button(row('Lejla'), 'Suspend')).toBeUndefined();
    expect(row('Lejla').textContent).toContain('You');
    expect(button(row('Tarik'), 'Verify')).toBeDefined();
    expect(button(row('Tarik'), 'Suspend')).toBeDefined();
    expect(button(row('Haris'), 'Reactivate')).toBeDefined();
    expect(row('Haris').textContent).toContain('Not a tasker');
  });

  it('suspends a user only after the admin confirms, then refreshes the overview', async () => {
    await open();

    answer = false;
    button(row('Tarik'), 'Suspend')!.click();
    await harness.fixture.whenStable();
    http.expectNone('/api/admin/users/u2/suspend');
    expect(asked[0].tone).toBe('danger');

    answer = true;
    button(row('Tarik'), 'Suspend')!.click();
    await harness.fixture.whenStable();
    http.expectOne({ method: 'POST', url: '/api/admin/users/u2/suspend' }).flush({ ...tasker, accountStatus: 'SUSPENDED' });
    http.expectOne('/api/admin/stats').flush({ ...stats, suspendedUsers: 3 });
    await harness.fixture.whenStable();

    expect(row('Tarik').textContent).toContain('Suspended');
    expect(button(row('Tarik'), 'Reactivate')).toBeDefined();
    expect(element().textContent).toContain('3 suspended');
  });

  it('verifies a tasker with one click', async () => {
    await open();

    button(row('Tarik'), 'Verify')!.click();
    http.expectOne({ method: 'POST', url: '/api/admin/tasker-profiles/p2/verify' }).flush({ verified: true });
    http.expectOne('/api/admin/stats').flush(stats);
    await harness.fixture.whenStable();

    expect(button(row('Tarik'), 'Verified')).toBeDefined();
  });

  it('asks the server with the filter and search from the address', async () => {
    await harness.navigateByUrl('/admin?filter=taskers&q=tarik&page=2');
    http.expectOne('/api/admin/stats').flush(stats);
    const request = users();
    request.flush(page([tasker]));
    await harness.fixture.whenStable();

    expect(request.request.params.get('role')).toBe('TASKER');
    expect(request.request.params.get('search')).toBe('tarik');
    expect(request.request.params.get('page')).toBe('1');
    expect(element().querySelector<HTMLInputElement>('input[type=search]')!.value).toBe('tarik');
  });

  it('opens the matching list from an overview tile', async () => {
    await open();

    button(element(), 'Removed tasks')!.click();
    await harness.fixture.whenStable();
    tasks().flush(page([]));
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/admin?tab=tasks&filter=removed');
  });

  it('removes a task with a reason and shows it as removed', async () => {
    await open('/admin?tab=tasks');

    expect(button(row('Old job'), 'Remove')).toBeUndefined();
    button(row('Leaking tap'), 'Remove')!.click();
    await harness.fixture.whenStable();

    const dialog = element().querySelector('dialog')!;
    button(dialog, 'Remove task')!.click();
    await harness.fixture.whenStable();
    http.expectNone('/api/admin/tasks/t1/remove');
    expect(dialog.textContent).toContain('Tell the owner why the task is removed.');

    button(dialog, 'Spam or advertising')!.click();
    await harness.fixture.whenStable();
    button(dialog, 'Remove task')!.click();
    const request = http.expectOne({ method: 'POST', url: '/api/admin/tasks/t1/remove' });
    expect(request.request.body).toEqual({ reason: 'Spam or advertising' });
    request.flush({ id: 't1', status: 'REMOVED' });
    http.expectOne('/api/admin/stats').flush({ ...stats, removedTasks: 1 });
    await harness.fixture.whenStable();

    expect(element().querySelector('dialog')).toBeNull();
    expect(row('Leaking tap').textContent).toContain('Removed by moderation');
  });

  it('keeps regular users out', async () => {
    TestBed.inject(AuthService).logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    TestBed.inject(AuthService).login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', expiresIn: 900, userId: 'u9', email: 'amra@test.ba', fullName: 'Amra', roles: ['CLIENT'] });

    await harness.navigateByUrl('/admin');

    expect(TestBed.inject(Router).url).toBe('/forbidden');
  });
});

describe('Admin query helpers', () => {
  it('turns filters into the server query and ignores unknown values', () => {
    expect(readUserQuery(convertToParamMap({ filter: 'suspended', q: 'amra' }))).toEqual({
      status: 'SUSPENDED',
      role: null,
      search: 'amra',
      page: 0,
    });
    expect(readUserQuery(convertToParamMap({ filter: 'hack', page: '-3' }))).toEqual({
      status: null,
      role: null,
      search: '',
      page: 0,
    });
    expect(readTaskQuery(convertToParamMap({ filter: 'open', page: '3' }))).toEqual({
      status: 'PUBLISHED',
      search: '',
      page: 2,
    });
  });
});
