import { Component, ElementRef, HostListener, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Bell, Briefcase, ChevronDown, ListTodo, LogOut, MessageSquare, Plus, Shield, SquareCheck } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { AuthService } from '../../auth/auth.service';
import { CurrentUser } from '../../auth/current-user';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-header',
  imports: [
    Icon,
    AsyncPipe,
    RouterLink,
    RouterLinkActive,
  ],
  templateUrl: './header.html',
})
export class Header {
  protected readonly icons = { Bell, Briefcase, ChevronDown, ListTodo, LogOut, MessageSquare, Plus, Shield, SquareCheck };

  readonly menuOpen = signal(false);

  constructor(
    protected authService: AuthService,
    private toastService: ToastService,
    private router: Router,
    private host: ElementRef<HTMLElement>,
  ) {}

  initials(user: CurrentUser): string {
    return user.fullName
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
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
    this.toastService.info('You have been logged out.');
    this.router.navigateByUrl('/tasks');
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.menuOpen() && !this.host.nativeElement.contains(event.target as Node)) {
      this.closeMenu();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
  }
}
