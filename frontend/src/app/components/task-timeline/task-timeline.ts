import { Component, Input } from '@angular/core';
import { Check, CircleCheckBig, Globe, Lock, Play, UserCheck } from 'lucide';
import { Icon } from '../icon/icon';
import { TaskDetail } from '../../api/models';
import { formatDate } from '../../shared/format/format';
import { LIFECYCLE, TaskStatus } from '../../shared/task-status/task-status';

type StepState = 'done' | 'current' | 'upcoming';

interface Step {
  status: TaskStatus;
  label: string;
  date: string;
  state: StepState;
}

const LABELS: Record<string, string> = {
  PUBLISHED: 'Published',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CLOSED: 'Closed',
};

@Component({
  selector: 'app-task-timeline',
  imports: [Icon],
  templateUrl: './task-timeline.html',
})
export class TaskTimeline {
  protected readonly icons = { Check, CircleCheckBig, Globe, Lock, Play, UserCheck };

  @Input({ required: true }) task!: TaskDetail;

  get steps(): Step[] {
    const current = LIFECYCLE.indexOf(this.task.status ?? 'DRAFT');
    const dates: Record<string, string | undefined> = {
      PUBLISHED: this.task.publishedAt,
      ASSIGNED: this.task.assignedAt,
      IN_PROGRESS: this.task.startedAt,
      COMPLETED: this.task.completedAt,
    };
    return LIFECYCLE.map((status, index) => ({
      status,
      label: LABELS[status],
      date: formatDate(dates[status]),
      state: this.stateOf(index, current),
    }));
  }

  private stateOf(index: number, current: number): StepState {
    if (index < current) {
      return 'done';
    }
    if (index > current) {
      return 'upcoming';
    }
    return this.task.status === 'CLOSED' ? 'done' : 'current';
  }

  get progress(): number {
    const current = LIFECYCLE.indexOf(this.task.status ?? 'DRAFT');
    return current <= 0 ? 0 : (current / (LIFECYCLE.length - 1)) * 100;
  }
}
