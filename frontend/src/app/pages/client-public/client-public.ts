import { Component, OnInit, signal } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ArrowLeft, BriefcaseBusiness, CalendarDays, Eye, LoaderCircle, MessageSquareQuote, SearchX, Star } from 'lucide';
import { ClientHire, ClientProfile, Review } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { Stars } from '../../components/stars/stars';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { ClientProfileService } from '../../services/client-profile.service';
import { ReviewService } from '../../services/review.service';
import { AuthService } from '../../auth/auth.service';
import { formatDate, timeAgo } from '../../shared/format/format';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';

export const CLIENT_PAGE_SIZE = 10;

export type ClientTab = 'reviews' | 'hires';

@Component({
  selector: 'app-client-public',
  imports: [RouterLink, CategoryIcon, Icon, Stars, StatusBadge, TranslatePipe, CategoryPipe],
  templateUrl: './client-public.html',
})
export class ClientPublic implements OnInit {
  protected readonly icons = { ArrowLeft, BriefcaseBusiness, CalendarDays, Eye, LoaderCircle, MessageSquareQuote, SearchX, Star };

  readonly profile = signal<ClientProfile | null>(null);
  readonly reviews = signal<Review[]>([]);
  readonly totalReviews = signal(0);
  readonly hires = signal<ClientHire[]>([]);
  readonly totalHires = signal(0);
  readonly tab = signal<ClientTab>('reviews');
  readonly notFound = signal(false);
  readonly failed = signal(false);
  readonly loadingMore = signal(false);
  readonly timeAgo = timeAgo;
  readonly formatDate = formatDate;

  private reviewsPage = 0;
  private hiresPage = 0;
  private userId = '';

  constructor(
    private clientProfileService: ClientProfileService,
    private reviewService: ReviewService,
    private route: ActivatedRoute,
    private location: Location,
    private title: Title,
    private authService: AuthService,
  ) {}

  get isOwn(): boolean {
    return this.authService.currentUser?.id === this.userId;
  }

  get isTasker(): boolean {
    return this.authService.hasRole('TASKER');
  }

  ngOnInit(): void {
    this.userId = this.route.snapshot.paramMap.get('userId') ?? '';
    this.load();
  }

  get hasMoreReviews(): boolean {
    return this.reviews().length < this.totalReviews();
  }

  get hasMoreHires(): boolean {
    return this.hires().length < this.totalHires();
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
      profile: this.clientProfileService.get(this.userId),
      reviews: this.reviewService.getReceived(this.userId, 0, CLIENT_PAGE_SIZE, 'CLIENT'),
      hires: this.clientProfileService.hires(this.userId, 0, CLIENT_PAGE_SIZE),
    }).subscribe({
      next: ({ profile, reviews, hires }) => {
        this.profile.set(profile);
        this.reviews.set(reviews.content ?? []);
        this.totalReviews.set(reviews.totalElements ?? 0);
        this.hires.set(hires.content ?? []);
        this.totalHires.set(hires.totalElements ?? 0);
        this.reviewsPage = 0;
        this.hiresPage = 0;
        this.title.setTitle(`${profile.fullName} · TaskNest`);
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

  showMoreReviews(): void {
    if (this.loadingMore() || !this.hasMoreReviews) {
      return;
    }
    this.loadingMore.set(true);
    this.reviewService.getReceived(this.userId, this.reviewsPage + 1, CLIENT_PAGE_SIZE, 'CLIENT').subscribe({
      next: (next) => {
        this.reviewsPage++;
        this.reviews.update((reviews) => [...reviews, ...(next.content ?? [])]);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  showMoreHires(): void {
    if (this.loadingMore() || !this.hasMoreHires) {
      return;
    }
    this.loadingMore.set(true);
    this.clientProfileService.hires(this.userId, this.hiresPage + 1, CLIENT_PAGE_SIZE).subscribe({
      next: (next) => {
        this.hiresPage++;
        this.hires.update((hires) => [...hires, ...(next.content ?? [])]);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  back(): void {
    this.location.back();
  }
}
