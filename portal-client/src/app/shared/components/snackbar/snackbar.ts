import { Component, inject } from '@angular/core';
import { SnackbarMessage, SnackbarService } from '../../../core/services/snackbar.service';

@Component({
  selector: 'app-snackbar',
  standalone: true,
  templateUrl: './snackbar.html',
  styleUrl: './snackbar.scss',
})
export class Snackbar {
  readonly snackbarService = inject(SnackbarService);

  dismiss(id: string): void {
    this.snackbarService.dismiss(id);
  }
}
