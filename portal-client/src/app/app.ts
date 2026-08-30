import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BrandingModal } from './shared/components/branding-modal/branding-modal';
import { Snackbar } from './shared/components/snackbar/snackbar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Snackbar, BrandingModal],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
