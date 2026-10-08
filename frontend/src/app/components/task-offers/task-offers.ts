import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BadgeCheck, ChevronDown, Handshake, Inbox, LoaderCircle, MessageSquare, RotateCcw, Star, TrendingDown } from 'lucide';
import { TaskDetail, TaskOffer } from '../../api/models';
import { OfferService } from '../../services/offer.service';
import { readApiError } from '../../shared/api-error';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { formatBudget, timeAgo } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { Select, SelectOption } from '../select/select';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';
import { t, translated } from '../../i18n/translate';

export type OfferSort = 'price' | 'rating' | 'newest';

export function sortOffers(offers: TaskOffer[], sort: OfferSort): TaskOffer[] {
  const byNewest = (a: TaskOffer, b: TaskOffer) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '');
  const compare: Record<OfferSort, (a: TaskOffer, b: TaskOffer) => number> = {
    price: (a, b) => (a.price ?? 0) - (b.price ?? 0) || byNewest(a, b),
    rating: (a, b) =>
      (b.taskerRating ?? -1) - (a.taskerRating ?? -1) ||
      (b.taskerCompletedJobs ?? 0) - (a.taskerCompletedJobs ?? 0) ||
      (a.price ?? 0) - (b.price ?? 0),
    newest: byNewest,
  };
  return [...offers].sort(compare[sort]);
}

@Component({
  selector: 'app-task-offers',
  imports: [FormsModule, RouterLink, Icon, Select, TranslatePipe, CategoryPipe],
  templateUrl: './task-offers.html',
})
export class TaskOffers implements OnChanges {
  protected readonly icons = { BadgeCheck, ChevronDown, Handshake, Inbox, LoaderCircle, MessageSquare, RotateCcw, Star, TrendingDown };

  @Input({ required: true }) task!: TaskDetail;
  @Output() accepted = new EventEmitter<void>();

  readonly sortOptions: SelectOption[] = [
    translated({ value: 'price' }, { label: 'offers.sortPrice' }),
    translated({ value: 'rating' }, { label: 'offers.sortRating' }),
    translated({ value: 'newest' }, { label: 'offers.sortNewest' }),
  ];

  readonly offers = signal<TaskOffer[] | null>(null);
  readonly failed = signal(false);
  readonly accepting = signal<string | null>(null);
  sort: OfferSort = 'price';

  readonly formatBudget = formatBudget;
  readonly timeAgo = timeAgo;

  constructor(
    private offerService: OfferService,
    private confirmService: ConfirmService,
    private toastService: ToastService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['task']) {
      this.load();
    }
  }

  get active(): TaskOffer[] {
    const list = (this.offers() ?? []).filter((offer) => offer.status === 'PENDING' || offer.status === 'ACCEPTED');
    const accepted = list.filter((offer) => offer.status === 'ACCEPTED');
    return [...accepted, ...sortOffers(list.filter((offer) => offer.status === 'PENDING'), this.sort)];
  }

  get declined(): TaskOffer[] {
    return (this.offers() ?? []).filter((offer) => offer.status === 'REJECTED' || offer.status === 'WITHDRAWN');
  }

  get cheapestId(): string | undefined {
    const pending = (this.offers() ?? []).filter((offer) => offer.status === 'PENDING');
    return pending.length > 1 ? sortOffers(pending, 'price')[0].id : undefined;
  }

  get canAccept(): boolean {
    return this.task.status === 'PUBLISHED';
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
    this.offerService.getTaskOffers(this.task.id!).subscribe({
      next: (offers) => this.offers.set(offers),
      error: () => this.failed.set(true),
    });
  }

  async accept(offer: TaskOffer): Promise<void> {
    const others = (this.offers() ?? []).filter((item) => item.status === 'PENDING' && item.id !== offer.id).length;
    const confirmed = await this.confirmService.ask({
      title: t('offers.hireTitle', { name: offer.taskerName, price: formatBudget(offer.price) }),
      message:
        others > 0
          ? t('offers.hireOthers', { name: offer.taskerName, count: others })
          : t('offers.hireOnly', { name: offer.taskerName }),
      confirmLabel: t('offers.hireConfirm'),
    });
    if (!confirmed) {
      return;
    }

    this.accepting.set(offer.id!);
    this.offerService.accept(offer.id!, offer.price).subscribe({
      next: () => {
        this.accepting.set(null);
        this.toastService.success(t('offers.hiredToast', { name: offer.taskerName }));
        this.accepted.emit();
      },
      error: (error) => {
        this.accepting.set(null);
        this.toastService.error(readApiError(error).message);
        this.load();
      },
    });
  }
}
