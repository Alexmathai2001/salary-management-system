import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly nav = [
    { path: '/employees', label: 'Employees' },
    { path: '/insights', label: 'Insights' },
    { path: '/outliers', label: 'Pay outliers' },
    { path: '/fx-rates', label: 'FX rates' },
  ];
}
