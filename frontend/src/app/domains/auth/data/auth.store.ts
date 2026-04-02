import { Injectable, inject, signal, computed, DestroyRef, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from './auth.service';
import { User, LoginRequest, RegisterRequest } from '../models/auth.models';
import { tap, finalize, catchError, EMPTY } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);

  private readonly _user = signal<User | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);
  private readonly _initialized = signal(false);

  readonly user = this._user.asReadonly();
  readonly error = this._error.asReadonly();
  readonly isLoading = this._loading.asReadonly();
  readonly isAuthenticated = computed(() => !!this._user());
  readonly isInitialized = this._initialized.asReadonly();

  checkSession(): Promise<void> {
    if (isPlatformBrowser(this.platformId)) {
      const path = window.location.pathname;
      if (path.includes('/auth/github/callback')) {
        this._initialized.set(true);
        return Promise.resolve();
      }
    }

    return new Promise((resolve) => {
      this.authService
        .me()
        .pipe(
          tap((user) => this._user.set(user)),
          catchError(() => {
            this._user.set(null);
            return EMPTY;
          }),
          finalize(() => {
            this._initialized.set(true);
            resolve();
          }),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe();
    });
  }

  login(credentials: LoginRequest) {
    this._loading.set(true);
    this._error.set(null);

    this.authService
      .login(credentials)
      .pipe(
        tap((res) => {
          this._user.set(res.user);
          this.router.navigate(['/home']);
        }),
        catchError((err: HttpErrorResponse) => {
          const message = err.error?.message || 'An unexpected error occurred';
          this._error.set(message);
          return EMPTY;
        }),
        finalize(() => this._loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  register(data: RegisterRequest) {
    this._loading.set(true);
    this._error.set(null);

    this.authService
      .register(data)
      .pipe(
        tap((res) => {
          this._user.set(res.user);
          this.router.navigate(['/home']);
        }),
        catchError((err: HttpErrorResponse) => {
          const message = err.error?.message || 'An unexpected error occurred';
          this._error.set(message);
          return EMPTY;
        }),
        finalize(() => this._loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  logout() {
    this._loading.set(true);

    this.authService
      .logout()
      .pipe(
        finalize(() => {
          this._user.set(null);
          this._loading.set(false);
          this.router.navigate(['/login']);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  setUser(user: User) {
    this._user.set(user);
  }

  clearError() {
    this._error.set(null);
  }
}
