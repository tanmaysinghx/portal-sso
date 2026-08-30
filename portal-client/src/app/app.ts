import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Snackbar } from './shared/components/snackbar/snackbar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Snackbar],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
