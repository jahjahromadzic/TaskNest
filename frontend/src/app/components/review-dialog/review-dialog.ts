import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CircleAlert, LoaderCircle, Star } from 'lucide';
import { Review } from '../../api/models';
import { ReviewService } from '../../services/review.service';
import { readApiError } from '../../shared/api-error';
import { Icon } from '../icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey, t } from '../../i18n/translate';

export const RATING_KEYS: TranslationKey[] = [
  'reviews.labels.one',
  'reviews.labels.two',
  'reviews.labels.three',
  'reviews.labels.four',
  'reviews.labels.five',
];

export function ratingLabel(rating: number): string {
  return rating > 0 ? t(RATING_KEYS[rating - 1]) : '';
}
export const COMMENT_MAX = 1000;

@Component({
  selector: 'app-review-dialog',
  imports: [FormsModule, Icon, TranslatePipe],
  templateUrl: './review-dialog.html',
})
export class ReviewDialog implements AfterViewInit {
  protected readonly icons = { CircleAlert, LoaderCircle, Star };

  @Input({ required: true }) taskId!: string;
  @Input({ required: true }) taskTitle!: string;
  @Input({ required: true }) revieweeName!: string;
  @Output() submitted = new EventEmitter<Review>();
  @Output() dismissed = new EventEmitter<void>();

  readonly ratingLabel = ratingLabel;
  readonly commentMax = COMMENT_MAX;
  readonly rating = signal(0);
  readonly hovered = signal(0);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  comment = '';

  @ViewChild('dialog', { static: true }) private dialog!: ElementRef<HTMLDialogElement>;

  constructor(private reviewService: ReviewService) {}

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  get shown(): number {
    return this.hovered() || this.rating();
  }

  onStarKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.rating.set(Math.min(5, this.rating() + 1));
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.rating.set(Math.max(1, this.rating() - 1));
    }
  }

  submit(): void {
    if (this.rating() === 0 || this.sending()) {
      this.error.set(this.rating() === 0 ? t('reviews.chooseStars') : null);
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.reviewService
      .create(this.taskId, { rating: this.rating(), comment: this.comment.trim() || undefined })
      .subscribe({
        next: (review) => {
          this.sending.set(false);
          this.submitted.emit(review);
        },
        error: (error) => {
          this.sending.set(false);
          this.error.set(readApiError(error).message);
        },
      });
  }

  onEscape(): void {
    if (!this.sending()) {
      this.dismissed.emit();
    }
  }
}
