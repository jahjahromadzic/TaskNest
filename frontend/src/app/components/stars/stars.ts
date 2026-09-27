import { Component, Input } from '@angular/core';
import { Star } from 'lucide';
import { Icon } from '../icon/icon';

@Component({
  selector: 'app-stars',
  imports: [Icon],
  template: `
    <span class="inline-flex items-center gap-0.5" [attr.aria-label]="rating + ' out of 5 stars'" role="img">
      @for (star of [1, 2, 3, 4, 5]; track star) {
        <svg [appIcon]="icons.Star" [class]="size"
             [class.text-accent]="star <= rating" [class.fill-accent]="star <= rating"
             [class.text-slate-300]="star > rating"></svg>
      }
    </span>
  `,
})
export class Stars {
  protected readonly icons = { Star };

  @Input({ required: true }) rating = 0;
  @Input() size = 'w-4 h-4';
}
