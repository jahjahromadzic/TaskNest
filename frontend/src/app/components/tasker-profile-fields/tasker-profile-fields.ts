import { Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Check, MapPin } from 'lucide';
import { Category, Municipality } from '../../api/models';
import { CategoryIcon } from '../category-icon/category-icon';
import { Icon } from '../icon/icon';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';

export const HEADLINE_MAX = 150;
export const BIO_MAX = 2000;

@Component({
  selector: 'app-tasker-profile-fields',
  imports: [FormsModule, CategoryIcon, Icon, TranslatePipe, CategoryPipe],
  templateUrl: './tasker-profile-fields.html',
  host: { class: 'block space-y-3.5' },
})
export class TaskerProfileFields {
  protected readonly icons = { Check, MapPin };
  readonly headlineMax = HEADLINE_MAX;
  readonly bioMax = BIO_MAX;

  readonly categories = input<Category[]>([]);
  readonly municipalities = input<Municipality[]>([]);
  readonly requireHeadline = input(false);

  readonly headline = model('');
  readonly bio = model('');
  readonly categoryIds = model<string[]>([]);
  readonly municipalityIds = model<string[]>([]);

  toggle(list: 'categoryIds' | 'municipalityIds', id: string | undefined): void {
    if (!id) {
      return;
    }
    this[list].update((ids) => (ids.includes(id) ? ids.filter((current) => current !== id) : [...ids, id]));
  }
}
