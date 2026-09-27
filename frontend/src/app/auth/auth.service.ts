import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, of } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../api/models';
import { CurrentUser, Role } from './current-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userSubject = new BehaviorSubject<CurrentUser | null>(null);
  readonly user$ = this.userSubject.asObservable();

  private accessToken: string | null = null;

  constructor(private http: HttpClient) {}

  get currentUser(): CurrentUser | null {
    return this.userSubject.value;
  }

  get token(): string | null {
    return this.accessToken;
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

  logout(): Observable<void> {
    this.endSession();
    return this.http.post<void>('/api/auth/logout', null).pipe(catchError(() => of(undefined)));
  }

  private startSession(response: AuthResponse): CurrentUser {
    const user: CurrentUser = {
      id: response.userId ?? '',
      email: response.email ?? '',
      fullName: response.fullName ?? '',
      roles: (response.roles ?? []) as Role[],
    };
    this.accessToken = response.token ?? null;
    this.userSubject.next(user);
    return user;
  }

  private endSession(): void {
    this.accessToken = null;
    this.userSubject.next(null);
  }
}
