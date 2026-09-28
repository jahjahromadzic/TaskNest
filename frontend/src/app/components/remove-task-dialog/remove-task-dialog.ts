import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LoaderCircle, ShieldAlert } from 'lucide';
import { TaskDetail } from '../../api/models';
import { AdminService } from '../../services/admin.service';
import { readApiError } from '../../shared/api-error';
import { Icon } from '../icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey, t } from '../../i18n/translate';

export const REASON_MAX = 250;

export const COMMON_REASONS: TranslationKey[] = [
  'remove.reasonSpam',
  'remove.reasonOffensive',
  'remove.reasonDuplicate',
  'remove.reasonNotReal',
];

@Component({
  selector: 'app-remove-task-dialog',
  imports: [FormsModule, Icon, TranslatePipe],
  templateUrl: './remove-task-dialog.html',
})
export class RemoveTaskDialog implements AfterViewInit {
  protected readonly icons = { LoaderCircle, ShieldAlert };

  @Input({ required: true }) taskId!: string;
  @Input({ required: true }) taskTitle!: string;
  @Output() removed = new EventEmitter<TaskDetail>();
  @Output() dismissed = new EventEmitter<void>();

  readonly reasonMax = REASON_MAX;
  readonly commonReasons = COMMON_REASONS;
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  reason = '';

  @ViewChild('dialog', { static: true }) private dialog!: ElementRef<HTMLDialogElement>;

  constructor(private adminService: AdminService) {}

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  pick(reason: TranslationKey): void {
    this.reason = t(reason);
    this.error.set(null);
  }

  submit(): void {
    const reason = this.reason.trim();
    if (!reason) {
      this.error.set(t('remove.required'));
      return;
    }
    if (this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.adminService.removeTask(this.taskId, reason).subscribe({
      next: (task) => {
        this.sending.set(false);
        this.removed.emit(task);
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
