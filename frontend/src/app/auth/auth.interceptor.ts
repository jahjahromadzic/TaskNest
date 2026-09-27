import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { ToastService } from '../shared/toast/toast.service';
import { AuthService } from './auth.service';

const PUBLIC_AUTH_URLS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/') || PUBLIC_AUTH_URLS.includes(request.url)) {
    return next(request);
  }

  const authService = inject(AuthService);
  const router = inject(Router);
  const toastService = inject(ToastService);

  if (!authService.token) {
    return next(request);
  }

  const send = () => {
    const token = authService.token;
    return next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request);
  };

  const sessionExpired = (error: unknown): Observable<never> => {
    if (!authService.currentUser) {
      toastService.info('Your session has expired. Please log in again.');
      router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
    }
    return throwError(() => error);
  };

  const renewAndSend = () => authService.refresh().pipe(catchError(sessionExpired), switchMap(send));

  if (authService.tokenExpiresSoon) {
    return renewAndSend();
  }

  return send().pipe(
    catchError((error) =>
      error instanceof HttpErrorResponse && error.status === 401 ? renewAndSend() : throwError(() => error),
    ),
  );
};
