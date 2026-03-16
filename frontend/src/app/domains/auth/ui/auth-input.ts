import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-auth-input',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div>
      <label [for]="id()" class="block text-sm font-medium text-muted mb-2">
        {{ label() }}
      </label>
      <input
        [id]="id()"
        [type]="type()"
        [formControl]="control()"
        [placeholder]="placeholder()"
        class="w-full px-4 py-3 rounded-xl bg-surface/50 border text-main placeholder:text-muted/50 focus:outline-none focus:ring-1 transition-colors"
        [class]="
          control().invalid && control().touched
            ? 'border-error focus:border-error focus:ring-error'
            : 'border-surface-emphasis focus:border-primary focus:ring-primary'
        "
      />
      @if (control().invalid && control().touched) {
        <p class="mt-1.5 text-xs text-error">{{ errorMessage() }}</p>
      } @else if (hint()) {
        <p class="mt-2 text-xs text-muted">{{ hint() }}</p>
      }
    </div>
  `,
})
export class AuthInput {
  id = input.required<string>();
  label = input.required<string>();
  type = input<'text' | 'email' | 'password'>('text');
  placeholder = input('');
  control = input.required<FormControl<string>>();
  errorMessage = input('');
  hint = input('');
}
