import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BottomNav } from './layout/bottom-nav/bottom-nav';
import { Header } from './layout/header/header';
import { Toasts } from './shared/toast/toasts';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, BottomNav, Toasts],
  templateUrl: './app.html',
})
export class App {}
