import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './auth/auth.guards';

const placeholder = () => import('./pages/placeholder/placeholder').then((m) => m.Placeholder);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tasks' },
  {
    path: 'tasks',
    title: 'Browse tasks · TaskNest',
    loadComponent: () => import('./pages/browse-tasks/browse-tasks').then((m) => m.BrowseTasks),
  },
  {
    path: 'tasks/:id',
    title: 'Task · TaskNest',
    loadComponent: () => import('./pages/task-detail/task-detail').then((m) => m.TaskDetailPage),
  },
  {
    path: '',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      {
        path: 'login',
        title: 'Log in · TaskNest',
        loadComponent: () => import('./pages/login/login').then((m) => m.Login),
      },
      {
        path: 'register',
        title: 'Sign up · TaskNest',
        loadComponent: () => import('./pages/register/register').then((m) => m.Register),
      },
    ],
  },
  {
    path: 'my-tasks',
    canActivate: [authGuard],
    title: 'My tasks · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'My tasks', phase: 2 },
  },
  {
    path: 'tasker',
    canActivate: [roleGuard('TASKER')],
    title: 'Tasker dashboard · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Tasker dashboard', phase: 3 },
  },
  {
    path: 'tasker/profile',
    canActivate: [roleGuard('TASKER')],
    title: 'Tasker profile · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Tasker profile', phase: 3 },
  },
  {
    path: 'messages',
    canActivate: [authGuard],
    title: 'Messages · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Messages', phase: 4 },
  },
  {
    path: 'notifications',
    canActivate: [authGuard],
    title: 'Notifications · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Notifications', phase: 4 },
  },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    title: 'Admin · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Admin', phase: 5 },
  },
  {
    path: 'forbidden',
    title: 'No access · TaskNest',
    loadComponent: () => import('./pages/forbidden/forbidden').then((m) => m.Forbidden),
  },
  {
    path: '**',
    title: 'Not found · TaskNest',
    loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound),
  },
];
