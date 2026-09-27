import { Component, OnInit, signal } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ArrowLeft, BadgeCheck, LoaderCircle, MapPin, MessageSquareQuote, SearchX, Star } from 'lucide';
import { Review, TaskerProfile } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { Stars } from '../../components/stars/stars';
import { ReviewService } from '../../services/review.service';
import { TaskerProfileService } from '../../services/tasker-profile.service';
import { timeAgo } from '../../shared/format/format';

export const REVIEWS_PER_PAGE = 10;

@Component({
  selector: 'app-tasker-public',
  imports: [CategoryIcon, Icon, Stars],
  templateUrl: './tasker-public.html',
})
export class TaskerPublic implements OnInit {
  protected readonly icons = { ArrowLeft, BadgeCheck, LoaderCircle, MapPin, MessageSquareQuote, SearchX, Star };

  readonly profile = signal<TaskerProfile | null>(null);
  readonly reviews = signal<Review[]>([]);
  readonly totalReviews = signal(0);
  readonly notFound = signal(false);
  readonly failed = signal(false);
  readonly loadingMore = signal(false);
  readonly timeAgo = timeAgo;

  private page = 0;
  private userId = '';

  constructor(
    private taskerProfileService: TaskerProfileService,
    private reviewService: ReviewService,
    private route: ActivatedRoute,
    private location: Location,
    private title: Title,
  ) {}

  ngOnInit(): void {
    this.userId = this.route.snapshot.paramMap.get('userId') ?? '';
    this.load();
  }

  get hasMore(): boolean {
    return this.reviews().length < this.totalReviews();
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
      profile: this.taskerProfileService.getOfUser(this.userId),
      reviews: this.reviewService.getReceived(this.userId, 0, REVIEWS_PER_PAGE),
    }).subscribe({
      next: ({ profile, reviews }) => {
        this.profile.set(profile);
        this.reviews.set(reviews.content ?? []);
        this.totalReviews.set(reviews.totalElements ?? 0);
        this.page = 0;
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

  showMore(): void {
    if (this.loadingMore() || !this.hasMore) {
      return;
    }
    this.loadingMore.set(true);
    this.reviewService.getReceived(this.userId, this.page + 1, REVIEWS_PER_PAGE).subscribe({
      next: (next) => {
        this.page++;
        this.reviews.update((reviews) => [...reviews, ...(next.content ?? [])]);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  back(): void {
    this.location.back();
  }
}
