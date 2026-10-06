import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Logout } from './core/logout';
import { SessionService } from './core/session.service';
import { SimulatiedatumService } from './core/simulatiedatum.service';
import { toonDatum } from './shared/datum';

@Component({
  imports: [RouterOutlet, RouterLink, Logout],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly title = signal('BankSim');
  protected readonly sessie = inject(SessionService);
  protected readonly simulatiedatum = inject(SimulatiedatumService);
  protected readonly datum = toonDatum;

  ngOnInit(): void {
    this.sessie.laad().catch(() => undefined);
  }
}
