import { Component, inject } from '@angular/core';
import { ToastRef, TOAST_DATA, ToastData } from '@core/services/notification.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  styles: `
    @keyframes slideIn {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    .toast-container {
      animation: slideIn 200ms ease-out;
    }
  `,
  template: `
    <div
      class="toast-container flex items-start gap-3 p-4 rounded-lg shadow-lg min-w-80 max-w-md"
      [class]="typeClasses[data.type]"
    >
      <div class="flex-shrink-0">
        @switch (data.type) {
          @case ('success') {
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
          }
          @case ('error') {
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          }
          @case ('warning') {
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          }
          @case ('info') {
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        }
      </div>
      <div class="flex-1">
        @if (data.title) {
          <p class="font-medium">{{ data.title }}</p>
        }
        <p class="text-sm opacity-90">{{ data.message }}</p>
      </div>
      <button
        type="button"
        (click)="dismiss()"
        class="flex-shrink-0 opacity-70 hover:opacity-100 transition-opacity"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  `,
})
export class Toast {
  private readonly toastRef = inject(ToastRef);
  protected readonly data = inject<ToastData>(TOAST_DATA);

  protected readonly typeClasses: Record<ToastData['type'], string> = {
    success: 'bg-green-600 text-white',
    error: 'bg-red-600 text-white',
    warning: 'bg-yellow-500 text-black',
    info: 'bg-blue-600 text-white',
  };

  dismiss() {
    this.toastRef.dismiss();
  }
}
