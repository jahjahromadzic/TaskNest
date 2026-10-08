import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { LoaderCircle, Tag } from 'lucide';
import { Offer } from '../../api/models';
import { ConversationService } from '../../services/conversation.service';
import { OfferService } from '../../services/offer.service';
import { readApiError } from '../../shared/api-error';
import { formatBudget } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

export const PRICE_NOTE_MAX = 300;

@Component({
  selector: 'app-offer-price-dialog',
  imports: [FormsModule, Icon, TranslatePipe],
  templateUrl: './offer-price-dialog.html',
})
export class OfferPriceDialog implements AfterViewInit {
  protected readonly icons = { LoaderCircle, Tag };

  @Input({ required: true }) offerId!: string;
  @Input({ required: true }) currentPrice!: number;
  @Input() conversationId: string | null = null;
  @Output() saved = new EventEmitter<Offer>();
  @Output() dismissed = new EventEmitter<void>();

  readonly noteMax = PRICE_NOTE_MAX;
  readonly formatBudget = formatBudget;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  price: number | null = null;
  note = '';

  @ViewChild('dialog', { static: true }) private dialog!: ElementRef<HTMLDialogElement>;

  constructor(
    private offerService: OfferService,
    private conversationService: ConversationService,
    private toastService: ToastService,
  ) {}

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  submit(): void {
    const price = this.price;
    if (price === null || !(price > 0)) {
      this.error.set(t('offerPrice.priceRequired'));
      return;
    }
    if (price === this.currentPrice) {
      this.error.set(t('offerPrice.samePrice'));
      return;
    }
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.offerService
      .updatePrice(this.offerId, price)
      .pipe(switchMap((offer) => this.announce(price).pipe(map(() => offer))))
      .subscribe({
        next: (offer) => {
          this.saving.set(false);
          this.toastService.success(t('offerPrice.saved', { price: formatBudget(price) }));
          this.saved.emit(offer);
        },
        error: (error) => {
          this.saving.set(false);
          this.error.set(readApiError(error).message);
        },
      });
  }

  onEscape(): void {
    if (!this.saving()) {
      this.dismissed.emit();
    }
  }

  private announce(price: number): Observable<unknown> {
    const note = this.note.trim();
    const text = t('offerPrice.chatMessage', { old: formatBudget(this.currentPrice), price: formatBudget(price) });
    const content = note ? `${text}\n${note}` : text;
    const conversationId$ = this.conversationId
      ? of(this.conversationId)
      : this.conversationService.forOffer(this.offerId).pipe(map((conversation) => conversation.id ?? ''));
    return conversationId$.pipe(
      switchMap((id) => (id ? this.conversationService.send(id, content) : of(null))),
      catchError(() => of(null)),
    );
  }
}
