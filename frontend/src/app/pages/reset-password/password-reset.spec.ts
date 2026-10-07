import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { currentLang } from '../../i18n/lang';
import { translateServerMessage } from '../../i18n/server-messages';
import { ToastService } from '../../shared/toast/toast.service';

describe('Password reset', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  function element(): HTMLElement {
    return harness.routeNativeElement!;
  }

  function text(): string {
    return (element().textContent ?? '').replace(/\s+/g, ' ');
  }

  async function type(selector: string, value: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await harness.fixture.whenStable();
  }

  it('links to the reset page from the login form', async () => {
    await harness.navigateByUrl('/login');

    expect(element().querySelector('a[href="/forgot-password"]')?.textContent).toContain('Forgot your password?');
  });

  it('asks for a link and shows the same answer whether or not the account exists', async () => {
    await harness.navigateByUrl('/forgot-password');
    expect(text()).toContain('Reset your password');

    await type('#forgot-email', ' amra@demo.tasknest.ba ');
    await submit();
    const request = http.expectOne({ method: 'POST', url: '/api/auth/password-reset/request' });
    expect(request.request.body).toEqual({ email: 'amra@demo.tasknest.ba' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await harness.fixture.whenStable();

    expect(text()).toContain('If an account with amra@demo.tasknest.ba exists, we sent it a link to choose a new password.');
  });

  it('sets the new password from the emailed link and sends the user to log in', async () => {
    await harness.navigateByUrl('/reset-password?token=abc123');
    await type('#reset-password', 'new-password-1');
    await type('#reset-repeat', 'new-password-1');

    await submit();
    const request = http.expectOne({ method: 'POST', url: '/api/auth/password-reset/confirm' });
    expect(request.request.body).toEqual({ token: 'abc123', password: 'new-password-1' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/login');
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Your password is changed. Log in with the new one.');
  });

  it('does not send passwords that do not match', async () => {
    await harness.navigateByUrl('/reset-password?token=abc123');
    await type('#reset-password', 'new-password-1');
    await type('#reset-repeat', 'new-password-2');

    await submit();

    http.expectNone('/api/auth/password-reset/confirm');
    expect(text()).toContain('The passwords do not match');
  });

  it('explains an expired link in Bosnian and offers a new one', async () => {
    currentLang.set('bs');
    await harness.navigateByUrl('/reset-password?token=old');
    await type('#reset-password', 'new-password-1');
    await type('#reset-repeat', 'new-password-1');

    await submit();
    http
      .expectOne('/api/auth/password-reset/confirm')
      .flush(
        { status: 400, detail: 'This reset link is invalid or has expired. Ask for a new one.' },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(text()).toContain('Ovaj link za promjenu lozinke nije važeći ili je istekao. Zatraži novi.');
    expect(element().querySelector('[role=alert] a[href="/forgot-password"]')).not.toBeNull();
  });

  it('asks for a new link when the token is missing from the address', async () => {
    await harness.navigateByUrl('/reset-password');

    expect(text()).toContain('This link is incomplete. Ask for a new one.');
  });

  it('translates the lockout after too many failed logins', () => {
    currentLang.set('bs');

    expect(translateServerMessage('Too many failed login attempts. Try again in 12 min.')).toBe(
      'Previše neuspjelih pokušaja prijave. Pokušaj ponovo za 12 min.',
    );
  });
});
