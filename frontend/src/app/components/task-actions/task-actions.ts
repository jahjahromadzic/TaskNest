import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { Ban, CircleCheckBig, LoaderCircle, Send, Undo2 } from 'lucide';
import { TaskDetail } from '../../api/models';
import { TaskService } from '../../services/task.service';
import { readApiError } from '../../shared/api-error';
import { ConfirmOptions, ConfirmService } from '../../shared/confirm/confirm.service';
import { daysLeft, timeAgo } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

export const AUTO_CLOSE_DAYS = 7;

type TaskAction = 'publish' | 'cancel' | 'reopen' | 'close';

@Component({
  selector: 'app-task-actions',
  imports: [Icon, TranslatePipe],
  templateUrl: './task-actions.html',
})
export class TaskActions {
  protected readonly icons = { Ban, CircleCheckBig, LoaderCircle, Send, Undo2 };

  @Input({ required: true }) task!: TaskDetail;
  @Output() changed = new EventEmitter<void>();

  readonly running = signal<TaskAction | null>(null);
  readonly timeAgo = timeAgo;

  constructor(
    private taskService: TaskService,
    private confirmService: ConfirmService,
    private toastService: ToastService,
  ) {}

  get tasker(): string {
    return this.task.assignedTaskerName ?? t('actions.theTasker');
  }

  get canCancel(): boolean {
    return ['DRAFT', 'PUBLISHED', 'ASSIGNED', 'IN_PROGRESS'].includes(this.task.status ?? '');
  }

  get autoCloseIn(): number | null {
    if (!this.task.completedAt) {
      return null;
    }
    const deadline = new Date(new Date(this.task.completedAt).getTime() + AUTO_CLOSE_DAYS * 86_400_000);
    return daysLeft(deadline.toISOString());
  }

  publish(): void {
    this.run('publish', this.taskService.publishTask(this.task.id!), t('actions.published'));
  }

  async cancel(): Promise<void> {
    const draft = this.task.status === 'DRAFT';
    const hired = this.task.status === 'ASSIGNED' || this.task.status === 'IN_PROGRESS';
    const confirmed = await this.ask({
      title: t(draft ? 'actions.discardTitle' : 'actions.cancelTitle'),
      message: draft
        ? t('actions.discardText')
        : hired
          ? t('actions.cancelHiredText', { name: this.tasker })
          : t('actions.cancelOpenText'),
      confirmLabel: t(draft ? 'actions.discardDraft' : 'actions.cancelTask'),
      cancelLabel: t('common.keepIt'),
      tone: 'danger',
    });
    if (confirmed) {
      this.run('cancel', this.taskService.cancelTask(this.task.id!), t(draft ? 'actions.draftDiscarded' : 'actions.taskCancelled'));
    }
  }

  async reopen(): Promise<void> {
    const confirmed = await this.ask({
      title: t('actions.reopenTitle'),
      message: t('actions.reopenText', { name: this.tasker }),
      confirmLabel: t('actions.reopenConfirm'),
    });
    if (confirmed) {
      this.run('reopen', this.taskService.reopenTask(this.task.id!), t('actions.reopened'));
    }
  }

  async close(): Promise<void> {
    const confirmed = await this.ask({
      title: t('actions.closeTitle'),
      message: t('actions.closeText', { name: this.tasker }),
      confirmLabel: t('actions.confirmClose'),
    });
    if (confirmed) {
      this.run('close', this.taskService.closeTask(this.task.id!), t('actions.closed', { name: this.tasker }));
    }
  }

  private ask(options: ConfirmOptions): Promise<boolean> {
    return this.running() ? Promise.resolve(false) : this.confirmService.ask(options);
  }

  private run(action: TaskAction, request: Observable<unknown>, success: string): void {
    if (this.running()) {
      return;
    }
    this.running.set(action);
    request.subscribe({
      next: () => {
        this.running.set(null);
        this.toastService.success(success);
        this.changed.emit();
      },
      error: (error) => {
        this.running.set(null);
        this.toastService.error(readApiError(error).message);
        this.changed.emit();
      },
    });
  }
}
