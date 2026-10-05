import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Logout } from './core/logout';
import { SessionService } from './core/session.service';

@Component({
  imports: [RouterOutlet, Logout],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly title = signal('BankSim');
  protected readonly sessie = inject(SessionService);

  ngOnInit(): void {
    void this.sessie.laad();
  }
}
