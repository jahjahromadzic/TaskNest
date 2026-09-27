import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ToastService } from '../shared/toast/toast.service';
import { AuthService } from './auth.service';
import { Role } from './current-user';
import { safeReturnUrl } from './return-url';

export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).currentUser) {
    return true;
  }
  inject(ToastService).info('Please log in to continue.');
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export function roleGuard(role: Role): CanActivateFn {
  return (route, state) => {
    const loggedIn = authGuard(route, state);
    if (loggedIn !== true) {
      return loggedIn;
    }
    return inject(AuthService).hasRole(role) || inject(Router).createUrlTree(['/forbidden']);
  };
}

export const guestGuard: CanActivateFn = (route) => {
  if (!inject(AuthService).currentUser) {
    return true;
  }
  return inject(Router).parseUrl(safeReturnUrl(route.queryParamMap.get('returnUrl')));
};

export const notTaskerGuard: CanActivateFn = (route, state) => {
  const loggedIn = authGuard(route, state);
  if (loggedIn !== true) {
    return loggedIn;
  }
  return !inject(AuthService).hasRole('TASKER') || inject(Router).parseUrl('/tasker/profile');
};
