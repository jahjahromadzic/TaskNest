import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideArrowRight, LucideClock, LucideHourglass, LucideMapPin } from '@lucide/angular';
import { TaskSummary } from '../../api/models';
import { daysLeft, formatBudget, timeAgo } from '../../shared/format/format';
import { CategoryIcon } from '../category-icon/category-icon';

@Component({
  selector: 'app-task-card',
  imports: [RouterLink, LucideArrowRight, LucideClock, LucideHourglass, LucideMapPin, CategoryIcon],
  templateUrl: './task-card.html',
})
export class TaskCard {
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
