import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthResponse } from '../api/models';
import { ToastService } from '../shared/toast/toast.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let authService: AuthService;

  const settle = () => new Promise((resolve) => setTimeout(resolve));

  function session(token: string, expiresIn = 900): AuthResponse {
    return { token, expiresIn, userId: 'u1', email: 'amra@test.ba', fullName: 'Amra Hodžić', roles: ['CLIENT'] };
  }

  function logIn(expiresIn = 900): void {
    authService.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush(session('first-token', expiresIn));
  }

  function unauthorized() {
    return { status: 401, statusText: 'Unauthorized' };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  it('sends no token for a visitor', () => {
    client.get('/api/tasks').subscribe();

    expect(http.expectOne('/api/tasks').request.headers.has('Authorization')).toBe(false);
  });

  it('adds the access token to API calls once logged in', () => {
    logIn();

    client.get('/api/tasks/mine').subscribe();

    expect(http.expectOne('/api/tasks/mine').request.headers.get('Authorization')).toBe('Bearer first-token');
  });

  it('does not add the token to the login, register, refresh and logout calls', () => {
    logIn();

    authService.logout().subscribe();

    expect(http.expectOne('/api/auth/logout').request.headers.has('Authorization')).toBe(false);
  });

  it('renews the token after a 401 and repeats the request with the new one', async () => {
    logIn();
    let body: unknown;
    client.get('/api/tasks/mine').subscribe((result) => (body = result));

    http.expectOne('/api/tasks/mine').flush({ detail: 'Token expired' }, unauthorized());
    http.expectOne('/api/auth/refresh').flush(session('second-token'));
    await settle();

    const retry = http.expectOne('/api/tasks/mine');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer second-token');
    retry.flush([{ id: 't1' }]);

    expect(body).toEqual([{ id: 't1' }]);
  });

  it('renews only once when several requests fail at the same time', async () => {
    logIn();
    client.get('/api/tasks/mine').subscribe();
    client.get('/api/notifications').subscribe();

    http.expectOne('/api/tasks/mine').flush(null, unauthorized());
    http.expectOne('/api/notifications').flush(null, unauthorized());
    http.expectOne('/api/auth/refresh').flush(session('second-token'));
    await settle();

    http.expectOne('/api/tasks/mine').flush([]);
    http.expectOne('/api/notifications').flush([]);
  });

  it('renews a token that is about to expire before sending the request', async () => {
    logIn(10);

    client.get('/api/tasks/mine').subscribe();

    http.expectNone('/api/tasks/mine');
    http.expectOne('/api/auth/refresh').flush(session('second-token'));
    await settle();

    expect(http.expectOne('/api/tasks/mine').request.headers.get('Authorization')).toBe('Bearer second-token');
  });

  it('logs out and sends the user to the login page when the session cannot be renewed', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/my-tasks');
    logIn();
    const toast = TestBed.inject(ToastService);
    let failed = false;
    client.get('/api/tasks/mine').subscribe({ error: () => (failed = true) });

    http.expectOne('/api/tasks/mine').flush(null, unauthorized());
    http.expectOne('/api/auth/refresh').flush({ detail: 'Refresh token has expired' }, unauthorized());
    await settle();
    await settle();

    expect(failed).toBe(true);
    expect(authService.currentUser).toBeNull();
    expect(router.url).toBe('/login?returnUrl=%2Fmy-tasks');
    expect(toast.toasts()[0].text).toContain('session has expired');
  });
});
