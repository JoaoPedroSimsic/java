import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthInput, AuthService, AuthStore } from '@domains/auth/index';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthInput],
  template: `
    <div class="min-h-screen bg-base relative overflow-hidden flex items-center justify-center px-6 py-12">
      <div
        class="absolute top-0 left-1/2 -translate-x-1/2 w-full h-full bg-[radial-gradient(circle_at_top,var(--color-surface)_0%,transparent_50%)] opacity-50 pointer-events-none"
      ></div>

      <div
        class="absolute top-40 right-20 w-80 h-80 bg-primary/20 rounded-full blur-3xl pointer-events-none"
      ></div>
      <div
        class="absolute bottom-10 left-10 w-64 h-64 bg-primary/10 rounded-full blur-3xl pointer-events-none"
      ></div>

      <div class="relative w-full max-w-md">
        <a routerLink="/" class="block text-center mb-10">
          <span class="text-3xl font-black tracking-tighter text-main">HERMES</span>
        </a>

        <div class="glass-light rounded-3xl p-8 md:p-10 shadow-2xl">
          <h1 class="text-2xl md:text-3xl font-bold text-main mb-2">Create your account</h1>
          <p class="text-muted mb-8">Join Hermes and start messaging</p>

          @if (authStore.error()) {
            <div
              class="mb-6 px-4 py-3 rounded-xl bg-error/10 border border-error/20 text-error text-sm"
            >
              {{ authStore.error() }}
            </div>
          }

          <form [formGroup]="registerForm" (ngSubmit)="handleRegister()" class="flex flex-col gap-5">
            <app-auth-input
              id="name"
              label="Full Name"
              type="text"
              placeholder="John Doe"
              [control]="registerForm.controls.name"
              [errorMessage]="nameErrorMessage"
            />

            <app-auth-input
              id="email"
              label="Email Address"
              type="email"
              placeholder="you@example.com"
              [control]="registerForm.controls.email"
              [errorMessage]="emailErrorMessage"
            />

            <app-auth-input
              id="password"
              label="Password"
              type="password"
              placeholder="Min. 8 characters"
              [control]="registerForm.controls.password"
              [errorMessage]="passwordErrorMessage"
              hint="Must be at least 8 characters"
            />

            <button
              type="submit"
              [disabled]="authStore.isLoading()"
              class="w-full py-3.5 px-6 mt-2 rounded-xl bg-primary text-inverse font-bold shadow-lg shadow-primary/20 hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
            >
              @if (authStore.isLoading()) {
                <span
                  class="animate-spin h-5 w-5 border-2 border-inverse border-t-transparent rounded-full"
                ></span>
                Creating account...
              } @else {
                Create Account
              }
            </button>
          </form>

          <div class="relative my-8">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-surface-emphasis"></div>
            </div>
            <div class="relative flex justify-center">
              <span class="px-4 text-sm text-muted bg-surface p-1 rounded-xl">
                or sign up with
              </span>
            </div>
          </div>

          <button
            type="button"
            (click)="signUpWithGitHub()"
            class="w-full py-3.5 px-6 rounded-xl bg-surface/50 border border-surface-emphasis text-main font-semibold hover:bg-surface-hover transition-all flex items-center justify-center gap-3"
          >
            <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
              <path
                d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"
              />
            </svg>
            Continue with GitHub
          </button>

          <p class="mt-6 text-xs text-center text-muted leading-relaxed">
            By creating an account, you agree to our
            <a href="#" class="text-primary hover:underline">Terms of Service</a>
            and
            <a href="#" class="text-primary hover:underline">Privacy Policy</a>
          </p>
        </div>

        <p class="mt-8 text-center text-muted">
          Already have an account?
          <a
            routerLink="/login"
            class="text-primary font-semibold hover:underline underline-offset-2"
          >
            Sign in
          </a>
        </p>
      </div>
    </div>
  `,
})
export class Register {
  protected readonly authStore = inject(AuthStore);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);

  registerForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  get nameErrorMessage(): string {
    const control = this.registerForm.controls.name;
    if (control.errors?.['required']) return 'Name is required';
    if (control.errors?.['minlength']) return 'Name must be at least 2 characters';
    return '';
  }

  get emailErrorMessage(): string {
    const control = this.registerForm.controls.email;
    if (control.errors?.['required']) return 'Email is required';
    if (control.errors?.['email']) return 'Please enter a valid email address';
    return '';
  }

  get passwordErrorMessage(): string {
    const control = this.registerForm.controls.password;
    if (control.errors?.['required']) return 'Password is required';
    if (control.errors?.['minlength']) return 'Password must be at least 8 characters';
    return '';
  }

  handleRegister() {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }
    this.authStore.register(this.registerForm.getRawValue());
  }

  signUpWithGitHub() {
    const redirectUri = `${window.location.origin}/auth/github/callback`;
    this.authService.getGitHubAuthUrl(redirectUri).subscribe((res) => {
      window.location.href = res.authUrl;
    });
  }
}
