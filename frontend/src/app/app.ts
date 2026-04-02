import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <main class="bg-base min-h-screen">
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly title = signal('hermes');
}
