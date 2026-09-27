import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {
  LucideBell,
  LucideGlobe,
  LucideHourglass,
  LucideMapPin,
  LucideMessageSquare,
  LucidePlus,
  LucideSquareCheck,
  LucideStar,
  LucideWrench,
} from '@lucide/angular';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    LucideBell,
    LucideGlobe,
    LucideHourglass,
    LucideMapPin,
    LucideMessageSquare,
    LucidePlus,
    LucideSquareCheck,
    LucideStar,
    LucideWrench,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
