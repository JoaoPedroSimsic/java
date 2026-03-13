import { Injectable, inject, signal, computed } from '@angular/core';
import { AuthService } from './auth.service';
import { User, AuthRequest } from '../models/auth.models';
import { tap, finalize } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly authService = inject(AuthService);

  private readonly _user = signal<User | null>(null);
  private readonly _loading = signal(false);

  readonly user = this._user.asReadonly();
  readonly isLoading = this._loading.asReadonly();
  readonly isAuthenticated = computed(() => !!this._user());

  login(credentials: AuthRequest) {
    this._loading.set(true);

    this.authService
      .login(credentials)
      .pipe(
        tap((res) => {
          this._user.set(res.user);
          localStorage.setItem('token', 'your_logic_here');
        }),
        finalize(() => this._loading.set(false)),
      )
      .subscribe();
  }

  logout() {
    this._user.set(null);
    localStorage.removeItem('token');
  }
}
