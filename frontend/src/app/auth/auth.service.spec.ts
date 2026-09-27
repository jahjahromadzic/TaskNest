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
});
