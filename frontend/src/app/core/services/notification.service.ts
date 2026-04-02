import { Injectable, InjectionToken, Injector, inject } from '@angular/core';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { Toast } from '@shared/ui/toast/toast';

export interface ToastData {
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  title?: string;
  duration?: number;
}

export const TOAST_DATA = new InjectionToken<ToastData>('TOAST_DATA');

export class ToastRef {
  constructor(private readonly overlayRef: OverlayRef) {}

  dismiss() {
    this.overlayRef.dispose();
  }
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly overlay = inject(Overlay);
  private readonly injector = inject(Injector);
  private readonly toasts: ToastRef[] = [];

  show(data: ToastData): ToastRef {
    const overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .global()
        .top(`${20 + this.toasts.length * 80}px`)
        .right('20px'),
      hasBackdrop: false,
    });

    const toastRef = new ToastRef(overlayRef);

    const injector = Injector.create({
      parent: this.injector,
      providers: [
        { provide: ToastRef, useValue: toastRef },
        { provide: TOAST_DATA, useValue: data },
      ],
    });

    const portal = new ComponentPortal(Toast, null, injector);
    overlayRef.attach(portal);

    this.toasts.push(toastRef);

    const duration = data.duration ?? 5000;
    if (duration > 0) {
      setTimeout(() => {
        this.remove(toastRef);
      }, duration);
    }

    return toastRef;
  }

  private remove(toastRef: ToastRef) {
    const index = this.toasts.indexOf(toastRef);
    if (index > -1) {
      this.toasts.splice(index, 1);
      toastRef.dismiss();
    }
  }

  success(message: string, title?: string) {
    return this.show({ type: 'success', message, title });
  }

  error(message: string, title?: string) {
    return this.show({ type: 'error', message, title });
  }

  warning(message: string, title?: string) {
    return this.show({ type: 'warning', message, title });
  }

  info(message: string, title?: string) {
    return this.show({ type: 'info', message, title });
  }
}
