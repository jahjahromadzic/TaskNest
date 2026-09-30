import { Component, OnInit, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of, throwError } from 'rxjs';
import {
  ArrowLeft,
  BadgeCheck,
  BriefcaseBusiness,
  CalendarDays,
  Eye,
  LoaderCircle,
  MapPin,
  MessageSquareQuote,
  SearchX,
  Star,
} from 'lucide';
import { ClientHire, ClientProfile, Review, TaskerProfile } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { Stars } from '../../components/stars/stars';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { ClientProfileService } from '../../services/client-profile.service';
import { ReviewService } from '../../services/review.service';
import { TaskerProfileService } from '../../services/tasker-profile.service';
import { formatDate, timeAgo } from '../../shared/format/format';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';

export const PROFILE_PAGE_SIZE = 10;

export type ProfileRole = 'tasker' | 'client';
export type ClientSection = 'reviews' | 'hires';

interface Page<T> {
  content?: T[];
  totalElements?: number;
}

export class PagedList<T> {
  readonly items = signal<T[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  private page = 0;

  constructor(private fetch: (page: number) => Observable<Page<T>>) {}

  get hasMore(): boolean {
    return this.items().length < this.total();
  }

  first(): Observable<Page<T>> {
    return this.fetch(0);
  }

  reset(page: Page<T>): void {
    this.page = 0;
    this.items.set(page.content ?? []);
    this.total.set(page.totalElements ?? 0);
  }

  more(): void {
    if (this.loading() || !this.hasMore) {
      return;
    }
    this.loading.set(true);
    this.fetch(this.page + 1).subscribe({
      next: (next) => {
        this.page++;
        this.items.update((items) => [...items, ...(next.content ?? [])]);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}

@Component({
  selector: 'app-user-profile',
  imports: [NgTemplateOutlet, RouterLink, CategoryIcon, Icon, Stars, StatusBadge, TranslatePipe, CategoryPipe],
  templateUrl: './user-profile.html',
})
export class UserProfile implements OnInit {
  protected readonly icons = {
    ArrowLeft,
    BadgeCheck,
    BriefcaseBusiness,
    CalendarDays,
    Eye,
    LoaderCircle,
    MapPin,
    MessageSquareQuote,
    SearchX,
    Star,
  };

  readonly client = signal<ClientProfile | null>(null);
  readonly tasker = signal<TaskerProfile | null>(null);
  readonly role = signal<ProfileRole>('client');
  readonly section = signal<ClientSection>('reviews');
  readonly notFound = signal(false);
  readonly failed = signal(false);
  readonly timeAgo = timeAgo;
  readonly formatDate = formatDate;

  readonly taskerReviews: PagedList<Review>;
  readonly clientReviews: PagedList<Review>;
  readonly hires: PagedList<ClientHire>;

  private userId = '';

  constructor(
    private clientProfileService: ClientProfileService,
    private taskerProfileService: TaskerProfileService,
    private reviewService: ReviewService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
    private location: Location,
    private title: Title,
  ) {
    this.taskerReviews = new PagedList((page) => this.reviewService.getReceived(this.userId, page, PROFILE_PAGE_SIZE, 'TASKER'));
    this.clientReviews = new PagedList((page) => this.reviewService.getReceived(this.userId, page, PROFILE_PAGE_SIZE, 'CLIENT'));
    this.hires = new PagedList((page) => this.clientProfileService.hires(this.userId, page, PROFILE_PAGE_SIZE));
  }

  ngOnInit(): void {
    this.userId = this.route.snapshot.paramMap.get('userId') ?? '';
    this.load();
  }

  get isOwn(): boolean {
    return this.authService.currentUser?.id === this.userId;
  }

  initials(name: string | undefined): string {
    return (name ?? '')
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }

  load(): void {
    this.failed.set(false);
    forkJoin({
      client: this.clientProfileService.get(this.userId),
      tasker: this.taskerProfileService.getOfUser(this.userId).pipe(
        catchError((error) => (error instanceof HttpErrorResponse && error.status === 404 ? of(null) : throwError(() => error))),
      ),
      taskerReviews: this.taskerReviews.first(),
      clientReviews: this.clientReviews.first(),
      hires: this.hires.first(),
    }).subscribe({
      next: ({ client, tasker, taskerReviews, clientReviews, hires }) => {
        this.client.set(client);
        this.tasker.set(tasker);
        this.taskerReviews.reset(taskerReviews);
        this.clientReviews.reset(clientReviews);
        this.hires.reset(hires);
        this.role.set(this.initialRole());
        this.title.setTitle(`${client.fullName} · TaskNest`);
      },
      error: (error) => {
        if (error instanceof HttpErrorResponse && (error.status === 404 || error.status === 400)) {
          this.notFound.set(true);
        } else {
          this.failed.set(true);
        }
      },
    });
  }

  selectRole(role: ProfileRole): void {
    if (role === this.role()) {
      return;
    }
    this.role.set(role);
    this.router.navigate([], { relativeTo: this.route, queryParams: { as: role }, replaceUrl: true });
  }

  back(): void {
    this.location.back();
  }

  private initialRole(): ProfileRole {
    const requested = this.route.snapshot.queryParamMap.get('as');
    const tasker = this.tasker();
    if (!tasker) {
      return 'client';
    }
    if (requested === 'tasker' || requested === 'client') {
      return requested;
    }
    const client = this.client();
    const asTasker = (tasker.completedJobsCount ?? 0) + this.taskerReviews.total();
    const asClient = (client?.postedTasksCount ?? 0) + (client?.hiresCount ?? 0);
    return asTasker >= asClient ? 'tasker' : 'client';
  }
}
