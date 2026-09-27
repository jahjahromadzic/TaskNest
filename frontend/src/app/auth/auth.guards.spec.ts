import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { routes } from '../app.routes';
import { ReferenceService } from '../services/reference.service';
import { TaskService } from '../services/task.service';
import { AuthService } from './auth.service';

describe('Route guards', () => {
  let harness: RouterTestingHarness;
  let router: Router;

  beforeAll(async () => {
    await import('../pages/browse-tasks/browse-tasks');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReferenceService, useValue: { getCategories: () => of([]), getMunicipalities: () => of([]) } },
        { provide: TaskService, useValue: { browse: () => of({ content: [], page: 0, totalElements: 0, totalPages: 0 }) } },
      ],
    });
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  function logInAs(...roles: string[]): void {
    TestBed.inject(AuthService).login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({
      token: 'access-token',
      expiresIn: 900,
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles,
    });
  }

  it('lets a visitor see the public task list', async () => {
    await harness.navigateByUrl('/tasks');

    expect(router.url).toBe('/tasks');
  });

  it('sends a visitor to the login page and remembers where they wanted to go', async () => {
    await harness.navigateByUrl('/my-tasks');

    expect(router.url).toBe('/login?returnUrl=%2Fmy-tasks');
  });

  it('lets a logged in client open their own pages', async () => {
    logInAs('CLIENT');

    await harness.navigateByUrl('/messages');

    expect(router.url).toBe('/messages');
  });

  it('shows no access when a client opens a tasker page', async () => {
    logInAs('CLIENT');

    await harness.navigateByUrl('/tasker');

    expect(router.url).toBe('/forbidden');
  });

  it('lets a tasker open the tasker dashboard', async () => {
    logInAs('CLIENT', 'TASKER');

    await harness.navigateByUrl('/tasker');

    expect(router.url).toBe('/tasker');
  });

  it('keeps everyone except admins out of the admin panel', async () => {
    logInAs('CLIENT', 'TASKER');
    await harness.navigateByUrl('/admin');
    expect(router.url).toBe('/forbidden');
  });

  it('lets an admin open the admin panel', async () => {
    logInAs('CLIENT', 'ADMIN');

    await harness.navigateByUrl('/admin');

    expect(router.url).toBe('/admin');
  });

  it('sends a visitor who is not logged in to the login page before checking the role', async () => {
    await harness.navigateByUrl('/admin');

    expect(router.url).toBe('/login?returnUrl=%2Fadmin');
  });

  it('does not show the login page to someone who is already logged in', async () => {
    logInAs('CLIENT');

    await harness.navigateByUrl('/login?returnUrl=/messages');

    expect(router.url).toBe('/messages');
  });

  it('sends a tasker who opens the become a tasker page to their profile instead', async () => {
    logInAs('CLIENT', 'TASKER');

    await harness.navigateByUrl('/become-a-tasker');

    expect(router.url).toBe('/tasker/profile');
  });

  it('lets a client open the become a tasker page', async () => {
    logInAs('CLIENT');

    await harness.navigateByUrl('/become-a-tasker');

    expect(router.url).toBe('/become-a-tasker');
  });
});
