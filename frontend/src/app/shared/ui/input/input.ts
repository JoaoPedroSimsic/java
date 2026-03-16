import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="w-full">
      <label [for]="id()" class="block text-sm font-medium text-gray-900">{{ label() }}</label>
      <input
        [id]="id()"
        [type]="type()"
        [formControl]="control()"
        class="mt-2 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-600 focus:ring-blue-600 sm:text-sm"
      />
    </div>
  `,
})
export class Input {
  label = input.required<string>();
  type = input<'text' | 'password' | 'email'>('text');
  id = input.required<string>();
  control = input.required<FormControl>();
}
