import {
  HttpErrorResponse,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { environment } from '../../environment/environment';
import { catchError, switchMap, throwError } from 'rxjs';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@domains/auth';

let isRefreshing = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  if (req.url.startsWith(environment.apiUrl) || (environment.apiUrl === '' && req.url.startsWith('/'))) {
    req = req.clone({ withCredentials: true });
  }

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthEndpoint =
        req.url.includes('/auth/refresh') ||
        req.url.includes('/auth/login') ||
        req.url.includes('/auth/register') ||
        req.url.includes('/auth/github');

      if (err.status === 401 && !isAuthEndpoint && !isRefreshing) {
        return handleTokenRefresh(req, next, router, authService);
      }

      if (err.status === 401 && isAuthEndpoint) {
        router.navigate(['/login']);
      }

      return throwError(() => err);
    }),
  );
};

function handleTokenRefresh(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  router: Router,
  authService: AuthService,
) {
  isRefreshing = true;

  return authService.refresh().pipe(
    switchMap(() => {
      isRefreshing = false;
      return next(req);
    }),
    catchError((refreshErr) => {
      isRefreshing = false;
      router.navigate(['/login']);
      return throwError(() => refreshErr);
    }),
  );
}
