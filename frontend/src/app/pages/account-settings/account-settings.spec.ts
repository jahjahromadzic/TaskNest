import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { Account } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { currentLang } from '../../i18n/lang';
import { ToastService } from '../../shared/toast/toast.service';

describe('Account settings page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let auth: AuthService;

  const account: Account = {
    id: 'u1',
    email: 'emina@test.ba',
    firstName: 'Emina',
    lastName: 'Begić',
    phone: '061 111 222',
    memberSince: '2026-03-14T10:00:00',
  };

  beforeAll(async () => {
    await import('./account-settings');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);

    auth.login({ email: 'emina@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'first-token',
      expiresIn: 900,
      userId: 'u1',
      email: 'emina@test.ba',
      fullName: 'Emina Begić',
      roles: ['CLIENT'],
    });

    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/settings');
    http.expectOne({ method: 'GET', url: '/api/account' }).flush(account);
    await harness.fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

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

  function button(label: string): HTMLButtonElement {
    return Array.from(page().querySelectorAll<HTMLButtonElement>('button')).find((candidate) =>
      candidate.textContent?.includes(label),
    )!;
  }

  async function click(label: string): Promise<void> {
    button(label).click();
    await harness.fixture.whenStable();
  }

  it('shows the account and saves changed details, renaming the user in the header too', async () => {
    expect(text()).toContain('emina@test.ba');
    expect(text()).toContain('Member since');
    expect(page().querySelector<HTMLInputElement>('#settings-phone')!.value).toBe('061 111 222');
    expect(page().querySelector<HTMLInputElement>('#settings-email')!.readOnly).toBe(true);
    expect(button('Save details').disabled).toBe(true);

    await type('#settings-last-name', ' Begić-Hasić ');
    await type('#settings-phone', '');
    expect(button('Save details').disabled).toBe(false);
    await click('Save details');

    const save = http.expectOne({ method: 'PUT', url: '/api/account' });
    expect(save.request.body).toEqual({ firstName: 'Emina', lastName: 'Begić-Hasić', phone: undefined });
    save.flush({ ...account, lastName: 'Begić-Hasić', phone: undefined });
    await harness.fixture.whenStable();

    expect(auth.currentUser?.fullName).toBe('Emina Begić-Hasić');
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Your details are saved.');
    expect(button('Save details').disabled).toBe(true);
  });

  it('does not send a new password that is repeated wrongly or equals the current one', async () => {
    await type('#settings-current-password', 'password123');
    await type('#settings-new-password', 'password123');
    await type('#settings-repeat-password', 'password123');
    await click('Change password');
    expect(text()).toContain('Choose a password different from the current one');

    await type('#settings-new-password', 'new-password-1');
    await type('#settings-repeat-password', 'new-password-2');
    await click('Change password');
    expect(text()).toContain('The passwords do not match');

    http.expectNone('/api/account/password');
  });

  it('changes the password, keeps this device signed in and empties the form', async () => {
    await type('#settings-current-password', 'password123');
    await type('#settings-new-password', 'new-password-1');
    await type('#settings-repeat-password', 'new-password-1');
    await click('Change password');

    const change = http.expectOne({ method: 'POST', url: '/api/account/password' });
    expect(change.request.body).toEqual({ currentPassword: 'password123', newPassword: 'new-password-1' });
    change.flush({
      token: 'second-token',
      expiresIn: 900,
      userId: 'u1',
      email: 'emina@test.ba',
      fullName: 'Emina Begić',
      roles: ['CLIENT'],
    });
    await harness.fixture.whenStable();

    expect(auth.token).toBe('second-token');
    expect(page().querySelector<HTMLInputElement>('#settings-current-password')!.value).toBe('');
    expect(page().querySelector<HTMLInputElement>('#settings-new-password')!.value).toBe('');
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Your password is changed.');
  });

  it('explains a wrong current password in Bosnian', async () => {
    currentLang.set('bs');
    await harness.fixture.whenStable();
    await type('#settings-current-password', 'not-my-password');
    await type('#settings-new-password', 'new-password-1');
    await type('#settings-repeat-password', 'new-password-1');
    await click('Promijeni lozinku');

    http
      .expectOne('/api/account/password')
      .flush({ status: 400, detail: 'The current password is not correct' }, { status: 400, statusText: 'Bad Request' });
    await harness.fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toContain('Trenutna lozinka nije tačna');
    expect(auth.token).toBe('first-token');
  });
});
