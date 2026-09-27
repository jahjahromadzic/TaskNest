import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Observable } from 'rxjs';
import { CircleCheckBig, Clock, Handshake, LoaderCircle, LogOut, Play, Send, Undo2 } from 'lucide';
import { Offer, TaskDetail } from '../../api/models';
import { OfferService } from '../../services/offer.service';
import { TaskService } from '../../services/task.service';
import { readApiError } from '../../shared/api-error';
import { ConfirmOptions, ConfirmService } from '../../shared/confirm/confirm.service';
import { daysLeft, formatBudget, timeAgo } from '../../shared/format/format';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { AUTO_CLOSE_DAYS } from '../task-actions/task-actions';

export const MESSAGE_MAX = 1000;

type TaskerAction = 'offer' | 'withdraw' | 'start' | 'complete';

@Component({
  selector: 'app-tasker-panel',
  imports: [FormsModule, Icon],
  templateUrl: './tasker-panel.html',
})
export class TaskerPanel implements OnChanges {
  protected readonly icons = { CircleCheckBig, Clock, Handshake, LoaderCircle, LogOut, Play, Send, Undo2 };

  @Input({ required: true }) task!: TaskDetail;
  @Output() changed = new EventEmitter<void>();

  readonly messageMax = MESSAGE_MAX;
  readonly offer = signal<Offer | null | undefined>(undefined);
  readonly running = signal<TaskerAction | null>(null);
  readonly attempted = signal(false);
  readonly formatBudget = formatBudget;
  readonly timeAgo = timeAgo;

  price: number | null = null;
  message = '';

  constructor(
    private offerService: OfferService,
    private taskService: TaskService,
    private confirmService: ConfirmService,
    private toastService: ToastService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['task']) {
      this.offerService.getMyOfferForTask(this.task.id!).subscribe({
        next: (offer) => this.offer.set(offer ?? null),
        error: () => this.offer.set(null),
      });
    }
  }

  get client(): string {
    return this.task.clientName?.split(' ')[0] ?? 'the client';
  }

  get hired(): boolean {
    return this.offer()?.status === 'ACCEPTED';
  }

  get autoCloseIn(): number | null {
    if (!this.task.completedAt) {
      return null;
    }
    const deadline = new Date(new Date(this.task.completedAt).getTime() + AUTO_CLOSE_DAYS * 86_400_000);
    return daysLeft(deadline.toISOString());
  }

  matchBudget(): void {
    this.price = this.task.budget ?? null;
  }

  sendOffer(form: NgForm): void {
    this.attempted.set(true);
    if (form.invalid || this.price === null || this.price <= 0) {
      return;
    }
    const request = this.offerService.submit(this.task.id!, {
      price: this.price,
      message: this.message.trim() || undefined,
    });
    this.run('offer', request, `Offer sent. ${this.client} will see it right away.`, () => {
      this.attempted.set(false);
      this.price = null;
      this.message = '';
    });
  }

  async withdraw(): Promise<void> {
    const offer = this.offer();
    if (!offer) {
      return;
    }
    const confirmed = await this.ask(
      this.hired
        ? {
            title: 'Back out of this job?',
            message: `${this.client} has to find someone else and the task opens for offers again. It is shown on your profile as a withdrawn job.`,
            confirmLabel: 'Back out',
            cancelLabel: 'Stay on the job',
            tone: 'danger',
          }
        : {
            title: 'Withdraw your offer?',
            message: `${this.client} will no longer see your offer of ${formatBudget(offer.price)}. You cannot send a new one on this task.`,
            confirmLabel: 'Withdraw offer',
            cancelLabel: 'Keep it',
            tone: 'danger',
          },
    );
    if (confirmed) {
      this.run('withdraw', this.offerService.withdraw(offer.id!), this.hired ? 'You backed out of the job.' : 'Offer withdrawn.');
    }
  }

  async start(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Start the work now?',
      message: `${this.client} gets a notification that you have started.`,
      confirmLabel: 'Start work',
    });
    if (confirmed) {
      this.run('start', this.taskService.startTask(this.task.id!), 'Good luck with the job!');
    }
  }

  async complete(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Is the job done?',
      message: `${this.client} is asked to check the work and confirm it. If they do nothing, the task closes by itself after ${AUTO_CLOSE_DAYS} days.`,
      confirmLabel: 'Mark as done',
    });
    if (confirmed) {
      this.run('complete', this.taskService.completeTask(this.task.id!), `Great work! ${this.client} has been asked to confirm.`);
    }
  }

  private ask(options: ConfirmOptions): Promise<boolean> {
    return this.running() ? Promise.resolve(false) : this.confirmService.ask(options);
  }

  private run(action: TaskerAction, request: Observable<unknown>, success: string, onSuccess?: () => void): void {
    if (this.running()) {
      return;
    }
    this.running.set(action);
    request.subscribe({
      next: () => {
        this.running.set(null);
        onSuccess?.();
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
