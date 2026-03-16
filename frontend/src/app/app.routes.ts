import { Routes } from '@angular/router';
import { authGuard } from '@domains/auth';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('@domains/marketing/features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'home',
    loadComponent: () => import('@domains/messaging').then((m) => m.Home),
    canActivate: [authGuard],
  },
  {
    path: 'login',
    loadComponent: () => import('@domains/auth/features/login/login').then((m) => m.Login),
  },
  {
    path: 'signup',
    loadComponent: () =>
      import('@domains/auth/features/register/register').then((m) => m.Register),
  },
  {
    path: 'auth/github/callback',
    loadComponent: () =>
      import('@domains/auth/features/github-callback/github-callback').then(
        (m) => m.GitHubCallback,
      ),
  },
  {
    path: '**',
    loadComponent: () => import('@shared/ui/not-found/not-found').then((m) => m.NotFound),
  },
];
