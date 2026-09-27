import { Routes } from '@angular/router';
import { authGuard, guestGuard, notTaskerGuard, roleGuard } from './auth/auth.guards';

const placeholder = () => import('./pages/placeholder/placeholder').then((m) => m.Placeholder);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tasks' },
  {
    path: 'tasks',
    title: 'Browse tasks · TaskNest',
    loadComponent: () => import('./pages/browse-tasks/browse-tasks').then((m) => m.BrowseTasks),
  },
  {
    path: 'tasks/new',
    canActivate: [roleGuard('CLIENT')],
    title: 'Post a task · TaskNest',
    loadComponent: () => import('./pages/post-task/post-task').then((m) => m.PostTask),
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
    canActivate: [roleGuard('CLIENT')],
    title: 'My tasks · TaskNest',
    loadComponent: () => import('./pages/my-tasks/my-tasks').then((m) => m.MyTasks),
  },
  {
    path: 'tasker',
    canActivate: [roleGuard('TASKER')],
    title: 'Tasker dashboard · TaskNest',
    loadComponent: () => import('./pages/tasker-dashboard/tasker-dashboard').then((m) => m.TaskerDashboard),
  },
  {
    path: 'tasker/profile',
    canActivate: [roleGuard('TASKER')],
    title: 'Tasker profile · TaskNest',
    loadComponent: () => import('./pages/tasker-profile/tasker-profile').then((m) => m.TaskerProfilePage),
  },
  {
    path: 'become-a-tasker',
    canActivate: [notTaskerGuard],
    title: 'Become a tasker · TaskNest',
    loadComponent: () => import('./pages/become-tasker/become-tasker').then((m) => m.BecomeTasker),
  },
  {
    path: 'taskers/:userId',
    canActivate: [authGuard],
    title: 'Tasker · TaskNest',
    loadComponent: () => import('./pages/tasker-public/tasker-public').then((m) => m.TaskerPublic),
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
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
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
