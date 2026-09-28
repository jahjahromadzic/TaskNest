import { Component, Input, OnChanges, SimpleChanges, signal } from '@angular/core';
import { Star } from 'lucide';
import { Review, TaskDetail } from '../../api/models';
import { ReviewService } from '../../services/review.service';
import { timeAgo } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { ReviewDialog } from '../review-dialog/review-dialog';
import { Stars } from '../stars/stars';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

@Component({
  selector: 'app-task-reviews',
  imports: [Icon, ReviewDialog, Stars, TranslatePipe],
  templateUrl: './task-reviews.html',
})
export class TaskReviews implements OnChanges {
  protected readonly icons = { Star };

  @Input({ required: true }) task!: TaskDetail;
  @Input({ required: true }) viewerId!: string;

  readonly reviews = signal<Review[] | null>(null);
  readonly writing = signal(false);
  readonly timeAgo = timeAgo;

  constructor(
    private reviewService: ReviewService,
    private toastService: ToastService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['task']) {
      this.reviewService.getTaskReviews(this.task.id!).subscribe({
        next: (reviews) => this.reviews.set(reviews),
        error: () => this.reviews.set([]),
      });
    }
  }

  get counterpartName(): string {
    return (this.viewerId === this.task.clientId ? this.task.assignedTaskerName : this.task.clientName) ?? '';
  }

  get canReview(): boolean {
    const reviews = this.reviews();
    return reviews !== null && !reviews.some((review) => review.reviewerId === this.viewerId);
  }

  onSubmitted(review: Review): void {
    this.writing.set(false);
    this.reviews.update((reviews) => [...(reviews ?? []), review]);
    this.toastService.success(t('reviews.posted'));
  }
}
