import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forbidden',
  templateUrl: './forbidden.html',
})
export class Forbidden {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;

  signOut(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/sign-in'));
  }
}
