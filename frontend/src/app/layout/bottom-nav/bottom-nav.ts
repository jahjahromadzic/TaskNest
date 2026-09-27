import { Component } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Bell, House, ListTodo, MessageSquare } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-bottom-nav',
  imports: [Icon, AsyncPipe, RouterLink, RouterLinkActive],
  templateUrl: './bottom-nav.html',
})
export class BottomNav {
  protected readonly icons = { Bell, House, ListTodo, MessageSquare };

  constructor(protected authService: AuthService) {}
}
