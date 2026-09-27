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

export const AUTO_CLOSE_DAYS = 7;

type TaskAction = 'publish' | 'cancel' | 'reopen' | 'close';

@Component({
  selector: 'app-task-actions',
  imports: [Icon],
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
    return this.task.assignedTaskerName ?? 'The tasker';
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
    this.run('publish', this.taskService.publishTask(this.task.id!), 'Your task is live. Taskers nearby can now send offers.');
  }

  async cancel(): Promise<void> {
    const draft = this.task.status === 'DRAFT';
    const hired = this.task.status === 'ASSIGNED' || this.task.status === 'IN_PROGRESS';
    const confirmed = await this.ask({
      title: draft ? 'Discard this draft?' : 'Cancel this task?',
      message: draft
        ? 'The draft is cancelled and can no longer be published.'
        : hired
          ? `${this.tasker} is no longer booked for this job. Only cancel if you have agreed on it with them.`
          : 'Taskers can no longer send offers and every pending offer is declined.',
      confirmLabel: draft ? 'Discard draft' : 'Cancel task',
      cancelLabel: 'Keep it',
      tone: 'danger',
    });
    if (confirmed) {
      this.run('cancel', this.taskService.cancelTask(this.task.id!), draft ? 'Draft discarded.' : 'Task cancelled.');
    }
  }

  async reopen(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Reopen the task for offers?',
      message: `${this.tasker} is released from the job and the task takes offers again for 30 days. Earlier offers become active again, so you can hire someone else right away.`,
      confirmLabel: 'Reopen',
    });
    if (confirmed) {
      this.run('reopen', this.taskService.reopenTask(this.task.id!), 'The task is open for offers again.');
    }
  }

  async close(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Confirm the job is done?',
      message: `The task is closed and counts as a finished job for ${this.tasker}. After that you can both leave a review.`,
      confirmLabel: 'Confirm and close',
    });
    if (confirmed) {
      this.run('close', this.taskService.closeTask(this.task.id!), `Task closed. Tell others how it went with ${this.tasker}.`);
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
