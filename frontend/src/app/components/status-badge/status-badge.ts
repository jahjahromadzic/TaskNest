import { Component, Input } from '@angular/core';
import { TASK_STATUS, TaskStatus } from '../../shared/task-status/task-status';

@Component({
  selector: 'app-status-badge',
  template: `
    @if (status) {
      <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full border text-xs font-semibold"
            [class]="style.badge">
        <span class="w-1.5 h-1.5 rounded-full bg-current" [class.animate-pulse]="live"></span>
        {{ style.label }}
      </span>
    }
  `,
})
export class StatusBadge {
  @Input() status: TaskStatus | undefined;

  get style() {
    return TASK_STATUS[this.status ?? 'DRAFT'];
  }

  get live(): boolean {
    return this.status === 'PUBLISHED' || this.status === 'IN_PROGRESS';
  }
}
