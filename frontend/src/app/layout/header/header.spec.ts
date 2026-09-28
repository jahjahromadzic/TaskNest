import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Header } from './header';
import { AuthService } from '../../auth/auth.service';

describe('Header', () => {
  let fixture: ComponentFixture<Header>;
  let http: HttpTestingController;
  let authService: AuthService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
  });

  function logIn(roles: string[]): void {
    authService.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'access-token',
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles,
    });
  }

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  async function openMenu(): Promise<void> {
    fixture.nativeElement.querySelector('button[aria-haspopup=menu]').click();
    await fixture.whenStable();
  }

  it('offers log in and sign up to a visitor', () => {
    expect(text()).toContain('Log in');
    expect(text()).toContain('Sign up');
  });

  it('shows the initials and a menu once logged in', async () => {
    logIn(['CLIENT']);
    await fixture.whenStable();

    expect(text()).toContain('AH');
    expect(text()).not.toContain('Sign up');

    await openMenu();
    expect(text()).toContain('amra@test.ba');
    expect(text()).toContain('My tasks');
    expect(text()).not.toContain('Tasker dashboard');
    expect(text()).toContain('Become a tasker');
    expect(text()).not.toContain('Admin');
  });

  it('shows the tasker and admin links only to those roles', async () => {
    logIn(['CLIENT', 'TASKER', 'ADMIN']);
    await fixture.whenStable();
    expect(text()).toContain('Administrator');

    await openMenu();
    expect(text()).toContain('Tasker dashboard');
    expect(text()).toContain('Tasker profile');
    expect(text()).not.toContain('Become a tasker');
    expect(text()).toContain('Admin');
  });

  it('goes back to the visitor view after logging out', async () => {
    logIn(['CLIENT']);
    await fixture.whenStable();
    await openMenu();

    const logOut = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button[role=menuitem]'))
      .find((button) => button.textContent?.includes('Log out'))!;
    logOut.click();
    http.expectOne('/api/auth/logout').flush(null);
    await fixture.whenStable();

    expect(authService.currentUser).toBeNull();
    expect(text()).toContain('Sign up');
  });
});
