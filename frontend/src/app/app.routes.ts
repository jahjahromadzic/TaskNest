import { Routes } from '@angular/router';

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
    loadComponent: placeholder,
    data: { heading: 'Task details', phase: 2 },
  },
  {
    path: '',
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
    title: 'My tasks · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'My tasks', phase: 2 },
  },
  {
    path: 'tasker',
    title: 'Tasker dashboard · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Tasker dashboard', phase: 3 },
  },
  {
    path: 'tasker/profile',
    title: 'Tasker profile · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Tasker profile', phase: 3 },
  },
  {
    path: 'messages',
    title: 'Messages · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Messages', phase: 4 },
  },
  {
    path: 'notifications',
    title: 'Notifications · TaskNest',
    loadComponent: placeholder,
    data: { heading: 'Notifications', phase: 4 },
  },
  {
    path: 'admin',
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
