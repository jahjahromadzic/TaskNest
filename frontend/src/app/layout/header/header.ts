import { Component, ElementRef, HostListener, ViewChild, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Briefcase, ChevronDown, ListTodo, LogOut, MessageSquare, Plus, Shield, SquareCheck, UserPen } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { AuthService } from '../../auth/auth.service';
import { CurrentUser } from '../../auth/current-user';
import { ToastService } from '../../shared/toast/toast.service';
import { NotificationBell } from '../notification-bell/notification-bell';
import { ConversationService } from '../../services/conversation.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';
import { LangSwitch } from '../../i18n/lang-switch';

@Component({
  selector: 'app-header',
  imports: [
    Icon,
    AsyncPipe,
    RouterLink,
    RouterLinkActive,
    NotificationBell,
    LangSwitch,
    TranslatePipe,
  ],
  templateUrl: './header.html',
})
export class Header {
  protected readonly icons = { Briefcase, ChevronDown, ListTodo, LogOut, MessageSquare, Plus, Shield, SquareCheck, UserPen };

  readonly menuOpen = signal(false);

  @ViewChild('menuRoot') private menuRoot?: ElementRef<HTMLElement>;

  constructor(
    protected authService: AuthService,
    protected conversationService: ConversationService,
    private toastService: ToastService,
    private router: Router,
  ) {}

  initials(user: CurrentUser): string {
    return user.fullName
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }

  roleLabel(user: CurrentUser): string {
    if (user.roles.includes('ADMIN')) {
      return t('nav.roleAdmin');
    }
    return user.roles.includes('TASKER') ? t('nav.roleTasker') : t('nav.roleClient');
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  logout(): void {
    this.closeMenu();
    this.authService.logout().subscribe();
    this.toastService.info(t('nav.loggedOut'));
    this.router.navigateByUrl('/tasks');
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.menuOpen() && !this.menuRoot?.nativeElement.contains(event.target as Node)) {
      this.closeMenu();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
  }
}
