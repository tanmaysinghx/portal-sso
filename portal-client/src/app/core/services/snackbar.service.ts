import { Injectable, signal } from '@angular/core';

export type SnackbarType = 'success' | 'error' | 'warning' | 'info';

export interface SnackbarMessage {
  id: string;
  type: SnackbarType;
  title: string;
  message?: string;
  code?: string;
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class SnackbarService {
  readonly messages = signal<SnackbarMessage[]>([]);

  show(payload: Omit<SnackbarMessage, 'id'>): string {
    const id = 'sb_' + Math.random().toString(36).substring(2, 9);
    const duration = payload.duration ?? (payload.type === 'error' ? 7000 : 4500);

    const messageItem: SnackbarMessage = {
      ...payload,
      id,
      duration,
    };

    this.messages.update((list) => [...list, messageItem]);

    if (duration > 0) {
      setTimeout(() => {
        this.dismiss(id);
      }, duration);
    }

    return id;
  }

  success(title: string, message?: string, code?: string): string {
    return this.show({ type: 'success', title, message, code });
  }

  error(title: string, message?: string, code?: string): string {
    return this.show({ type: 'error', title, message, code });
  }

  warning(title: string, message?: string, code?: string): string {
    return this.show({ type: 'warning', title, message, code });
  }

  info(title: string, message?: string, code?: string): string {
    return this.show({ type: 'info', title, message, code });
  }

  dismiss(id: string): void {
    this.messages.update((list) => list.filter((m) => m.id !== id));
  }

  clearAll(): void {
    this.messages.set([]);
  }
}
