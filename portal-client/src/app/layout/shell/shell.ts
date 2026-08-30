import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;
  readonly initial = computed(() => (this.currentUser()?.email ?? '?').charAt(0).toUpperCase());

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/sign-in'));
  }
}
