import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Flag, LoaderCircle } from 'lucide';
import { ReportReason } from '../../api/models';
import { ReportService, ReportTarget } from '../../services/report.service';
import { readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey, t } from '../../i18n/translate';

export const REPORT_COMMENT_MAX = 500;

export const REPORT_REASONS: ReportReason[] = ['SPAM', 'FRAUD', 'INAPPROPRIATE', 'NO_SHOW', 'OTHER'];

export function reasonKey(reason: ReportReason | undefined): TranslationKey {
  return `report.reasons.${reason ?? 'OTHER'}` as TranslationKey;
}

@Component({
  selector: 'app-report-dialog',
  imports: [FormsModule, Icon, TranslatePipe],
  templateUrl: './report-dialog.html',
})
export class ReportDialog implements AfterViewInit {
  protected readonly icons = { Flag, LoaderCircle };

  @Input({ required: true }) target!: ReportTarget;
  @Input({ required: true }) targetId!: string;
  @Input({ required: true }) targetName!: string;
  @Output() sent = new EventEmitter<void>();
  @Output() dismissed = new EventEmitter<void>();

  readonly reasons = REPORT_REASONS;
  readonly reasonKey = reasonKey;
  readonly commentMax = REPORT_COMMENT_MAX;
  readonly reason = signal<ReportReason | null>(null);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  comment = '';

  @ViewChild('dialog', { static: true }) private dialog!: ElementRef<HTMLDialogElement>;

  constructor(
    private reportService: ReportService,
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

  pick(reason: ReportReason): void {
    this.reason.set(reason);
    this.error.set(null);
  }

  submit(): void {
    const reason = this.reason();
    const comment = this.comment.trim();
    if (!reason) {
      this.error.set(t('report.reasonRequired'));
      return;
    }
    if (reason === 'OTHER' && !comment) {
      this.error.set(t('report.commentRequired'));
      return;
    }
    if (this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.reportService.report(this.target, this.targetId, { reason, comment: comment || undefined }).subscribe({
      next: () => {
        this.sending.set(false);
        this.toastService.success(t('report.sent'));
        this.sent.emit();
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
