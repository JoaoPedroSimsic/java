import { inject, Injectable, signal } from '@angular/core';
import { User, AuthRequest, AuthResponse } from '../models/auth.models';
import { tap, catchError, Observable, throwError } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environment/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  readonly currentUser = signal<User | null>(null);
  readonly isLoading = signal<boolean>(false);

  login(request: AuthRequest): Observable<AuthResponse> {
    this.isLoading.set(true);

    return this.http
      .post<AuthResponse>(`${this.API_URL}/auth/login`, request, { withCredentials: true })
      .pipe(
        tap((res) => {
          this.currentUser.set(res.user);
          this.isLoading.set(false);
        }),
        catchError((err) => {
          this.isLoading.set(false);
          return throwError(() => err);
        }),
      );
  }

  checkAuth(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/auth/me`, { withCredentials: true }).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError((err) => {
        this.currentUser.set(null);
        return throwError(() => err);
      }),
    );
  }
}
