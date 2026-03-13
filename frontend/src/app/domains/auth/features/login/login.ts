import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Input } from '@shared/ui/input/input';
import { AuthStore } from '@domains/auth/index';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, Input],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  protected readonly authStore = inject(AuthStore);
  private fb = inject(NonNullableFormBuilder);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  handleLogin() {
    if (this.loginForm.valid) {
      this.authStore.login(this.loginForm.getRawValue());
    }
  }
}
