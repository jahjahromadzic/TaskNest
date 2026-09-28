import { Routes } from '@angular/router';
import { authGuard, guestGuard, notTaskerGuard, roleGuard } from './auth/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tasks' },
  {
    path: 'tasks',
    title: 'titles.browse',
    loadComponent: () => import('./pages/browse-tasks/browse-tasks').then((m) => m.BrowseTasks),
  },
  {
    path: 'tasks/new',
    canActivate: [roleGuard('CLIENT')],
    title: 'titles.postTask',
    loadComponent: () => import('./pages/post-task/post-task').then((m) => m.PostTask),
  },
  {
    path: 'tasks/:id',
    title: 'titles.task',
    loadComponent: () => import('./pages/task-detail/task-detail').then((m) => m.TaskDetailPage),
  },
  {
    path: '',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      {
        path: 'login',
        title: 'titles.login',
        loadComponent: () => import('./pages/login/login').then((m) => m.Login),
      },
      {
        path: 'register',
        title: 'titles.register',
        loadComponent: () => import('./pages/register/register').then((m) => m.Register),
      },
    ],
  },
  {
    path: 'my-tasks',
    canActivate: [roleGuard('CLIENT')],
    title: 'titles.myTasks',
    loadComponent: () => import('./pages/my-tasks/my-tasks').then((m) => m.MyTasks),
  },
  {
    path: 'tasker',
    canActivate: [roleGuard('TASKER')],
    title: 'titles.taskerDashboard',
    loadComponent: () => import('./pages/tasker-dashboard/tasker-dashboard').then((m) => m.TaskerDashboard),
  },
  {
    path: 'tasker/profile',
    canActivate: [roleGuard('TASKER')],
    title: 'titles.taskerProfile',
    loadComponent: () => import('./pages/tasker-profile/tasker-profile').then((m) => m.TaskerProfilePage),
  },
  {
    path: 'become-a-tasker',
    canActivate: [notTaskerGuard],
    title: 'titles.becomeTasker',
    loadComponent: () => import('./pages/become-tasker/become-tasker').then((m) => m.BecomeTasker),
  },
  {
    path: 'taskers/:userId',
    canActivate: [authGuard],
    title: 'titles.tasker',
    loadComponent: () => import('./pages/tasker-public/tasker-public').then((m) => m.TaskerPublic),
  },
  {
    path: 'clients/:userId',
    canActivate: [authGuard],
    title: 'titles.client',
    loadComponent: () => import('./pages/client-public/client-public').then((m) => m.ClientPublic),
  },
  {
    path: 'messages',
    canActivate: [authGuard],
    title: 'titles.messages',
    loadComponent: () => import('./pages/messages/messages').then((m) => m.Messages),
  },
  {
    path: 'notifications',
    canActivate: [authGuard],
    title: 'titles.notifications',
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
  },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    title: 'titles.admin',
    loadComponent: () => import('./pages/admin/admin').then((m) => m.Admin),
  },
  {
    path: 'forbidden',
    title: 'titles.forbidden',
    loadComponent: () => import('./pages/forbidden/forbidden').then((m) => m.Forbidden),
  },
  {
    path: '**',
    title: 'titles.notFound',
    loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound),
  },
];
