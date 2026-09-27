import { Component } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LucideBell, LucideHouse, LucideListTodo, LucideMessageSquare } from '@lucide/angular';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-bottom-nav',
  imports: [AsyncPipe, RouterLink, RouterLinkActive, LucideBell, LucideHouse, LucideListTodo, LucideMessageSquare],
  templateUrl: './bottom-nav.html',
})
export class BottomNav {
  constructor(protected authService: AuthService) {}
}
