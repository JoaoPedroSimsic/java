import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService, AuthStore } from '@domains/auth/index';
import { catchError, EMPTY, tap } from 'rxjs';

@Component({
  selector: 'app-github-callback',
  standalone: true,
  template: `
    <div class="flex items-center justify-center min-h-screen">
      <div class="text-center">
        @if (error) {
          <div class="text-red-400 mb-4">{{ error }}</div>
          <a routerLink="/login" class="text-hermes-primary hover:underline">Back to login</a>
        } @else {
          <div class="animate-spin h-8 w-8 border-4 border-hermes-primary border-t-transparent rounded-full mx-auto mb-4"></div>
          <p class="text-gray-400">Completing GitHub sign in...</p>
        }
      </div>
    </div>
  `,
})
export class GitHubCallback implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly authStore = inject(AuthStore);

  error: string | null = null;

  ngOnInit() {
    const code = this.route.snapshot.queryParamMap.get('code');
    const error = this.route.snapshot.queryParamMap.get('error');

    if (error) {
      this.error = 'GitHub authentication was cancelled or failed.';
      return;
    }

    if (!code) {
      this.error = 'No authorization code received from GitHub.';
      return;
    }

    const redirectUri = `${window.location.origin}/auth/github/callback`;

    this.authService
      .handleGitHubCallback(code, redirectUri)
      .pipe(
        tap((res) => {
          this.authStore.setUser(res.user);
          this.router.navigate(['/home']);
        }),
        catchError((err) => {
          this.error = err.error?.message || 'Failed to complete GitHub sign in.';
          return EMPTY;
        }),
      )
      .subscribe();
  }
}
