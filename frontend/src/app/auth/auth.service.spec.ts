import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { CurrentUser } from './current-user';
import { AuthResponse } from '../api/models';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const response: AuthResponse = {
    token: 'access-token',
    expiresIn: 900,
    userId: 'u1',
    email: 'amra@test.ba',
    fullName: 'Amra Hodžić',
    roles: ['CLIENT'],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts a session after a successful login', () => {
    const seen: (CurrentUser | null)[] = [];
    service.user$.subscribe((user) => seen.push(user));

    service.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'amra@test.ba', password: 'password123' });
    request.flush(response);

    expect(service.token).toBe('access-token');
    expect(service.currentUser).toEqual({ id: 'u1', email: 'amra@test.ba', fullName: 'Amra Hodžić', roles: ['CLIENT'] });
    expect(seen.map((user) => user?.email ?? null)).toEqual([null, 'amra@test.ba']);
    expect(service.hasRole('CLIENT')).toBe(true);
    expect(service.hasRole('ADMIN')).toBe(false);
  });

  it('stays logged out when the login fails', () => {
    let failed = false;
    service.login({ email: 'amra@test.ba', password: 'wrong' }).subscribe({ error: () => (failed = true) });

    http.expectOne('/api/auth/login').flush(
      { detail: 'Invalid email or password' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(failed).toBe(true);
    expect(service.currentUser).toBeNull();
    expect(service.token).toBeNull();
  });

  it('starts a session right after registering', () => {
    service
      .register({ email: 'amra@test.ba', password: 'password123', firstName: 'Amra', lastName: 'Hodžić' })
      .subscribe();

    http.expectOne('/api/auth/register').flush(response);

    expect(service.currentUser?.fullName).toBe('Amra Hodžić');
  });

  it('forgets the user on logout even when the server call fails', () => {
    service.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush(response);

    let completed = false;
    service.logout().subscribe({ complete: () => (completed = true) });

    expect(service.currentUser).toBeNull();
    expect(service.token).toBeNull();

    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });
    expect(completed).toBe(true);
  });

  describe('refresh', () => {
    const settle = () => new Promise((resolve) => setTimeout(resolve));

    it('starts a session from the refresh cookie without sending a body', async () => {
      service.refresh().subscribe();

      const request = http.expectOne('/api/auth/refresh');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toBeNull();
      request.flush(response);
      await settle();

      expect(service.currentUser?.email).toBe('amra@test.ba');
      expect(service.token).toBe('access-token');
    });

    it('sends only one request when several callers refresh at the same time', async () => {
      const results: string[] = [];
      service.refresh().subscribe((user) => results.push(user.email));
      service.refresh().subscribe((user) => results.push(user.email));

      http.expectOne('/api/auth/refresh').flush(response);
      await settle();

      expect(results).toEqual(['amra@test.ba', 'amra@test.ba']);
    });

    it('ends the session when the server rejects the refresh cookie', async () => {
      service.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
      http.expectOne('/api/auth/login').flush(response);

      service.refresh().subscribe({ error: () => undefined });
      http
        .expectOne('/api/auth/refresh')
        .flush({ detail: 'Refresh token has already been used' }, { status: 401, statusText: 'Unauthorized' });
      await settle();

      expect(service.currentUser).toBeNull();
      expect(service.token).toBeNull();
    });

    it('keeps the session when the server cannot be reached', async () => {
      service.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
      http.expectOne('/api/auth/login').flush(response);

      service.refresh().subscribe({ error: () => undefined });
      http.expectOne('/api/auth/refresh').error(new ProgressEvent('error'), { status: 0 });
      await settle();

      expect(service.currentUser?.email).toBe('amra@test.ba');
    });

    it('lets the app start as a visitor when there is no session to restore', async () => {
      let done = false;
      service.restoreSession().subscribe({ complete: () => (done = true) });

      http
        .expectOne('/api/auth/refresh')
        .flush({ detail: 'Refresh token is missing' }, { status: 401, statusText: 'Unauthorized' });
      await settle();

      expect(done).toBe(true);
      expect(service.currentUser).toBeNull();
    });
  });

  it('turns the client into a tasker and fetches a token with the new role', async () => {
    service.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush(response);

    service.becomeTasker().subscribe();
    http.expectOne({ method: 'POST', url: '/api/auth/activate-tasker' }).flush({ roles: ['CLIENT', 'TASKER'] });
    http.expectOne('/api/auth/refresh').flush({ ...response, token: 'tasker-token', roles: ['CLIENT', 'TASKER'] });
    await new Promise((resolve) => setTimeout(resolve));

    expect(service.hasRole('TASKER')).toBe(true);
    expect(service.token).toBe('tasker-token');
  });
});
