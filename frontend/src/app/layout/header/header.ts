import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideSquareCheck } from '@lucide/angular';

@Component({
  selector: 'app-header',
  imports: [RouterLink, LucideSquareCheck],
  templateUrl: './header.html',
})
export class Header {}
