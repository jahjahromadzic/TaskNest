import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ArrowRight, Clock, Hourglass, MapPin } from 'lucide';
import { Icon } from '../icon/icon';
import { TaskSummary } from '../../api/models';
import { daysLeft, formatBudget, timeAgo } from '../../shared/format/format';
import { CategoryIcon } from '../category-icon/category-icon';

@Component({
  selector: 'app-task-card',
  imports: [Icon, RouterLink, CategoryIcon],
  templateUrl: './task-card.html',
})
export class TaskCard {
  protected readonly icons = { ArrowRight, Clock, Hourglass, MapPin };

  @Input({ required: true }) task!: TaskSummary;

  get budget(): string {
    return formatBudget(this.task.budget);
  }

  get postedAgo(): string {
    return timeAgo(this.task.publishedAt);
  }

  get daysLeft(): number | null {
    return daysLeft(this.task.expiresAt);
  }
}
