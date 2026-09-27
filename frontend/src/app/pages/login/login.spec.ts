import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';

describe('Login page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  beforeAll(async () => {
    await import('./login');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function fillAndSubmit(email: string, password: string): Promise<void> {
    const page: HTMLElement = harness.routeNativeElement!;
    typeInto(page.querySelector<HTMLInputElement>('#login-email')!, email);
    typeInto(page.querySelector<HTMLInputElement>('#login-password')!, password);
    page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await harness.fixture.whenStable();
  }

  function typeInto(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('does not call the server when the form is empty', async () => {
    await harness.navigateByUrl('/login');

    await fillAndSubmit('', '');

    http.expectNone('/api/auth/login');
    expect(harness.routeNativeElement?.textContent).toContain('Enter your email address');
  });

  it('shows the server message when the password is wrong', async () => {
    await harness.navigateByUrl('/login');

    await fillAndSubmit('amra@test.ba', 'wrong-password');
    http
      .expectOne('/api/auth/login')
      .flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement?.querySelector('[role=alert]')?.textContent).toContain('Invalid email or password');
    expect(TestBed.inject(AuthService).currentUser).toBeNull();
  });

  it('logs in and goes back to the page that asked for it', async () => {
    await harness.navigateByUrl('/login?returnUrl=/become-a-tasker');

    await fillAndSubmit('amra@test.ba', 'password123');
    http.expectOne('/api/auth/login').flush({
      token: 'access-token',
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles: ['CLIENT'],
    });
    await harness.fixture.whenStable();

    expect(TestBed.inject(AuthService).currentUser?.email).toBe('amra@test.ba');
    expect(TestBed.inject(Router).url).toBe('/become-a-tasker');
  });
});
