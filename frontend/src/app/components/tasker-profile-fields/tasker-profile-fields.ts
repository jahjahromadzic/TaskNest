import { Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Check, MapPin, X } from 'lucide';
import { Category, Municipality } from '../../api/models';
import { CategoryIcon } from '../category-icon/category-icon';
import { Icon } from '../icon/icon';
import { Select, SelectOption } from '../select/select';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';
import { regionLabel, regionsOf, sortByRegion } from '../../shared/municipalities/municipalities';

export const HEADLINE_MAX = 150;
export const BIO_MAX = 2000;

const DEFAULT_REGION = 'Sarajevo Canton';

@Component({
  selector: 'app-tasker-profile-fields',
  imports: [FormsModule, CategoryIcon, Icon, Select, TranslatePipe, CategoryPipe],
  templateUrl: './tasker-profile-fields.html',
  host: { class: 'block space-y-3.5' },
})
export class TaskerProfileFields {
  protected readonly icons = { Check, MapPin, X };
  protected readonly regionLabel = regionLabel;
  readonly headlineMax = HEADLINE_MAX;
  readonly bioMax = BIO_MAX;

  readonly categories = input<Category[]>([]);
  readonly municipalities = input<Municipality[]>([]);
  readonly requireHeadline = input(false);

  readonly headline = model('');
  readonly bio = model('');
  readonly categoryIds = model<string[]>([]);
  readonly municipalityIds = model<string[]>([]);

  private readonly pickedRegion = signal<string | null>(null);
  private readonly sorted = computed(() => sortByRegion(this.municipalities()));
  private readonly regions = computed(() => regionsOf(this.municipalities()));

  readonly region = computed(() => {
    const regions = this.regions();
    const picked = this.pickedRegion();
    if (picked !== null && regions.includes(picked)) {
      return picked;
    }
    const ids = this.municipalityIds();
    const firstSelected = this.sorted().find((municipality) => ids.includes(municipality.id!));
    if (firstSelected) {
      return firstSelected.region ?? '';
    }
    return regions.includes(DEFAULT_REGION) ? DEFAULT_REGION : (regions[0] ?? '');
  });

  readonly regionOptions = computed<SelectOption[]>(() => {
    const ids = this.municipalityIds();
    return this.regions().map((region) => {
      const count = this.sorted().filter(
        (municipality) => (municipality.region ?? '') === region && ids.includes(municipality.id!),
      ).length;
      const label = regionLabel(region);
      return { value: region, label: count > 0 ? `${label} (${count})` : label };
    });
  });

  readonly inRegion = computed(() =>
    this.sorted().filter((municipality) => (municipality.region ?? '') === this.region()),
  );

  readonly elsewhere = computed(() => {
    const ids = this.municipalityIds();
    return this.sorted().filter(
      (municipality) => (municipality.region ?? '') !== this.region() && ids.includes(municipality.id!),
    );
  });

  readonly wholeRegionSelected = computed(() => {
    const ids = this.municipalityIds();
    return this.inRegion().every((municipality) => ids.includes(municipality.id!));
  });

  pickRegion(region: string): void {
    this.pickedRegion.set(region);
  }

  toggle(list: 'categoryIds' | 'municipalityIds', id: string | undefined): void {
    if (!id) {
      return;
    }
    if (list === 'municipalityIds') {
      this.pickedRegion.set(this.region());
    }
    this[list].update((ids) => (ids.includes(id) ? ids.filter((current) => current !== id) : [...ids, id]));
  }

  toggleRegion(): void {
    const regionIds = this.inRegion().map((municipality) => municipality.id!);
    this.pickedRegion.set(this.region());
    if (this.wholeRegionSelected()) {
      this.municipalityIds.update((ids) => ids.filter((id) => !regionIds.includes(id)));
    } else {
      this.municipalityIds.update((ids) => [...ids, ...regionIds.filter((id) => !ids.includes(id))]);
    }
  }
}
