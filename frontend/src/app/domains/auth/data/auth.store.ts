import { Injectable, inject, signal, computed } from '@angular/core';
import { AuthService } from './auth.service';
import { User, AuthRequest } from '../models/auth.models';
import { tap, finalize, catchError, EMPTY } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly authService = inject(AuthService);

  private readonly _user = signal<User | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly user = this._user.asReadonly();
  readonly error = this._error.asReadonly();
  readonly isLoading = this._loading.asReadonly();
  readonly isAuthenticated = computed(() => !!this._user());

  login(credentials: AuthRequest) {
    this._loading.set(true);
    this._error.set(null);

    this.authService
      .login(credentials)
      .pipe(
        tap((res) => {
          this._user.set(res.user);
        }),
        catchError((err: HttpErrorResponse) => {
          const message = err.error?.message || 'An unexpected error occurred';
          this._error.set(message);
          return EMPTY;
        }),
        finalize(() => this._loading.set(false)),
      )
      .subscribe();
  }

  logout() {
    this.authService
      .logout()
      .pipe(
        finalize(() => {
          this._user.set(null);
        }),
      )
      .subscribe();
  }
}
