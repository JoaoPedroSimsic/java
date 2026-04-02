import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environment/environment';
import {
  AuthResponse,
  GitHubAuthResponse,
  LoginRequest,
  RegisterRequest,
  User,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = environment.apiUrl;

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/login`, credentials);
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/register`, data);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/logout`, {});
  }

  me(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/auth/me`);
  }

  refresh(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/refresh`, {});
  }

  getGitHubAuthUrl(redirectUri: string): Observable<GitHubAuthResponse> {
    return this.http.get<GitHubAuthResponse>(`${this.API_URL}/auth/github`, {
      params: { redirectUri },
    });
  }

  handleGitHubCallback(code: string, redirectUri: string): Observable<AuthResponse> {
    return this.http.get<AuthResponse>(`${this.API_URL}/auth/github/callback`, {
      params: { code, redirectUri },
    });
  }
}
