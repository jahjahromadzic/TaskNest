import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Account, UpdateAccountRequest } from '../api/models';
import { AuthService } from '../auth/auth.service';

@Injectable({ providedIn: 'root' })
export class AccountService {
  constructor(
    private http: HttpClient,
    private authService: AuthService,
  ) {}

  getAccount(): Observable<Account> {
    return this.http.get<Account>('/api/account');
  }

  updateAccount(request: UpdateAccountRequest): Observable<Account> {
    return this.http
      .put<Account>('/api/account', request)
      .pipe(tap((account) => this.authService.rename(`${account.firstName} ${account.lastName}`)));
  }
}
