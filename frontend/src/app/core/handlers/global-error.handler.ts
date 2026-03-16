import { ErrorHandler, Injectable, inject, NgZone } from '@angular/core';
import { NotificationService } from '@core/services/notification.service';

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notificationService = inject(NotificationService);
  private readonly zone = inject(NgZone);

  handleError(error: Error): void {
    console.error('Global error:', error);

    this.zone.run(() => {
      const message = this.getErrorMessage(error);
      this.notificationService.error(message, 'Error');
    });
  }

  private getErrorMessage(error: Error): string {
    if (error.message?.includes('Http failure')) {
      return 'Unable to connect to the server. Please try again.';
    }

    if (error.message?.includes('ChunkLoadError')) {
      return 'Failed to load application. Please refresh the page.';
    }

    return 'An unexpected error occurred. Please try again.';
  }
}
