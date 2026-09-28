import { Component, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { Ban, BadgeCheck, ClipboardList, Shield, ShieldAlert, Users } from 'lucide';
import type { IconNode } from 'lucide';
import { AdminStats } from '../../api/models';
import { Icon } from '../../components/icon/icon';
import { AdminService } from '../../services/admin.service';
import { AdminTab, readTab } from './admin-query';
import { AdminTasks } from './admin-tasks';
import { AdminUsers } from './admin-users';
import { t } from '../../i18n/translate';
import { TranslatePipe } from '../../i18n/translate.pipe';

interface StatTile {
  readonly label: string;
  value: number;
  readonly note: string;
  icon: IconNode;
  tone: string;
  tab: AdminTab;
  filter: string;
}

export function statTiles(stats: AdminStats): StatTile[] {
  return [
    {
      get label() {
        return t('admin.tileUsers');
      },
      value: stats.users ?? 0,
      get note() {
        return t('admin.tileUsersNote', { count: stats.suspendedUsers ?? 0 });
      },
      icon: Users,
      tone: 'bg-brand-50 text-brand',
      tab: 'users',
      filter: 'suspended',
    },
    {
      get label() {
        return t('admin.tileTaskers');
      },
      value: stats.taskers ?? 0,
      get note() {
        return t('admin.tileTaskersNote', { count: stats.unverifiedTaskers ?? 0 });
      },
      icon: BadgeCheck,
      tone: 'bg-sky-50 text-sky-600',
      tab: 'users',
      filter: 'taskers',
    },
    {
      get label() {
        return t('admin.tileOpen');
      },
      value: stats.openTasks ?? 0,
      get note() {
        return t('admin.tileOpenNote');
      },
      icon: ClipboardList,
      tone: 'bg-emerald-50 text-emerald-600',
      tab: 'tasks',
      filter: 'open',
    },
    {
      get label() {
        return t('admin.tileRemoved');
      },
      value: stats.removedTasks ?? 0,
      get note() {
        return t('admin.tileRemovedNote');
      },
      icon: Ban,
      tone: 'bg-red-50 text-red-600',
      tab: 'tasks',
      filter: 'removed',
    },
  ];
}

@Component({
  selector: 'app-admin',
  imports: [Icon, AdminUsers, AdminTasks, TranslatePipe],
  templateUrl: './admin.html',
})
export class Admin implements OnInit {
  protected readonly icons = { Shield, ShieldAlert };

  readonly tiles = signal<StatTile[] | null>(null);
  readonly tab;
  readonly skeletons = [1, 2, 3, 4];

  constructor(
    private adminService: AdminService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.tab = toSignal(this.route.queryParamMap.pipe(map(readTab)), { initialValue: 'users' as AdminTab });
  }

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.adminService.stats().subscribe({
      next: (stats) => this.tiles.set(statTiles(stats)),
      error: () => this.tiles.set([]),
    });
  }

  open(tab: AdminTab, filter: string | null = null): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tab === 'users' ? null : tab, filter },
    });
  }
}
