import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ArrowRight, Clock, Hourglass, MapPin } from 'lucide';
import { Icon } from '../icon/icon';
import { TaskSummary } from '../../api/models';
import { daysLeft, formatBudget, timeAgo } from '../../shared/format/format';
import { CategoryIcon } from '../category-icon/category-icon';
import { StatusBadge } from '../status-badge/status-badge';

@Component({
  selector: 'app-task-card',
  imports: [Icon, RouterLink, CategoryIcon, StatusBadge],
  templateUrl: './task-card.html',
})
export class TaskCard {
  protected readonly icons = { ArrowRight, Clock, Hourglass, MapPin };

  @Input({ required: true }) task!: TaskSummary;
  @Input() showStatus = false;
  @Input() note: string | null = null;

  get budget(): string {
    return formatBudget(this.task.budget);
  }

  get postedAgo(): string {
    return timeAgo(this.task.publishedAt);
  }

  get daysLeft(): number | null {
    return this.task.status === 'PUBLISHED' ? daysLeft(this.task.expiresAt) : null;
  }
}
