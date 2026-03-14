import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './input.html',
  styleUrl: './input.css',
})
export class Input {
  label = input.required<string>();
  type = input<'text' | 'password' | 'email'>('text');
  id = input.required<string>();
  control = input.required<FormControl>();
}
