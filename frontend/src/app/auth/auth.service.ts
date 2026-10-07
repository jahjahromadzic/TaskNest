import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  BehaviorSubject,
  Observable,
  catchError,
  defer,
  finalize,
  firstValueFrom,
  map,
  of,
  shareReplay,
  switchMap,
  throwError,
} from 'rxjs';
import { AuthResponse, BecomeTaskerRequest, ChangePasswordRequest, LoginRequest, RegisterRequest } from '../api/models';
import { CurrentUser, Role } from './current-user';

const REFRESH_LOCK = 'tasknest-token-refresh';
const EXPIRY_MARGIN_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userSubject = new BehaviorSubject<CurrentUser | null>(null);
  readonly user$ = this.userSubject.asObservable();

  private accessToken: string | null = null;
  private expiresAt = 0;
  private refreshInFlight: Observable<CurrentUser> | null = null;

  constructor(private http: HttpClient) {}

  get currentUser(): CurrentUser | null {
    return this.userSubject.value;
  }

  get token(): string | null {
    return this.accessToken;
  }

  get tokenExpiresSoon(): boolean {
    return this.accessToken !== null && Date.now() > this.expiresAt - EXPIRY_MARGIN_MS;
  }

  hasRole(role: Role): boolean {
    return this.currentUser?.roles.includes(role) ?? false;
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.http
      .post<AuthResponse>('/api/auth/login', request)
      .pipe(map((response) => this.startSession(response)));
  }

  register(request: RegisterRequest): Observable<CurrentUser> {
    return this.http
      .post<AuthResponse>('/api/auth/register', request)
      .pipe(map((response) => this.startSession(response)));
  }

  refresh(): Observable<CurrentUser> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.requestRefresh().pipe(
        map((response) => this.startSession(response)),
        catchError((error) => {
          if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
            this.endSession();
          }
          return throwError(() => error);
        }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1),
      );
    }
    return this.refreshInFlight;
  }

  becomeTasker(request: BecomeTaskerRequest): Observable<CurrentUser> {
    return this.http.post('/api/auth/activate-tasker', request).pipe(switchMap(() => this.refresh()));
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/password-reset/request', { email });
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>('/api/auth/password-reset/confirm', { token, password });
  }

  changePassword(request: ChangePasswordRequest): Observable<CurrentUser> {
    return this.http
      .post<AuthResponse>('/api/account/password', request)
      .pipe(map((response) => this.startSession(response)));
  }

  rename(fullName: string): void {
    const user = this.currentUser;
    if (user) {
      this.userSubject.next({ ...user, fullName });
    }
  }

  restoreSession(): Observable<void> {
    return this.refresh().pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  logout(): Observable<void> {
    this.endSession();
    return this.http.post<void>('/api/auth/logout', null).pipe(catchError(() => of(undefined)));
  }

  private requestRefresh(): Observable<AuthResponse> {
    const call = () => firstValueFrom(this.http.post<AuthResponse>('/api/auth/refresh', null));
    const locks = globalThis.navigator?.locks;
    return defer(() => (locks ? locks.request(REFRESH_LOCK, call) : call()));
  }

  private startSession(response: AuthResponse): CurrentUser {
    const user: CurrentUser = {
      id: response.userId ?? '',
      email: response.email ?? '',
      fullName: response.fullName ?? '',
      roles: (response.roles ?? []) as Role[],
    };
    this.accessToken = response.token ?? null;
    this.expiresAt = Date.now() + (response.expiresIn ?? 0) * 1000;
    this.userSubject.next(user);
    return user;
  }

  private endSession(): void {
    this.accessToken = null;
    this.expiresAt = 0;
    this.userSubject.next(null);
  }
}
