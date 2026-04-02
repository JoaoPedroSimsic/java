import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="flex flex-col items-center justify-center min-h-screen text-center px-4">
      <h1 class="text-6xl font-bold text-hermes-primary mb-4">404</h1>
      <h2 class="text-2xl font-semibold mb-2">Page Not Found</h2>
      <p class="text-gray-400 mb-8">
        The page you're looking for doesn't exist or has been moved.
      </p>
      <a
        routerLink="/"
        class="bg-hermes-primary text-white px-6 py-2 rounded-md hover:opacity-90 transition-opacity"
      >
        Back to Home
      </a>
    </div>
  `,
})
export class NotFound {}
