import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable, concat, forkJoin, last } from 'rxjs';
import { BadgeCheck, Check, Circle, CircleCheck, LoaderCircle, MapPin, PartyPopper, Save, Star } from 'lucide';
import { Category, Municipality, TaskerProfile } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { ReferenceService } from '../../services/reference.service';
import { TaskerProfileService } from '../../services/tasker-profile.service';
import { readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';

export const HEADLINE_MAX = 150;
export const BIO_MAX = 2000;

interface Snapshot {
  headline: string;
  bio: string;
  categoryIds: string[];
  municipalityIds: string[];
}

function sameIds(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((id) => b.includes(id));
}

@Component({
  selector: 'app-tasker-profile',
  imports: [FormsModule, CategoryIcon, Icon],
  templateUrl: './tasker-profile.html',
})
export class TaskerProfilePage implements OnInit {
  protected readonly icons = { BadgeCheck, Check, Circle, CircleCheck, LoaderCircle, MapPin, PartyPopper, Save, Star };

  readonly headlineMax = HEADLINE_MAX;
  readonly bioMax = BIO_MAX;

  readonly profile = signal<TaskerProfile | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly municipalities = signal<Municipality[]>([]);
  readonly failed = signal(false);
  readonly saving = signal(false);
  readonly welcome: boolean;

  readonly headline = signal('');
  readonly bio = signal('');
  readonly categoryIds = signal<string[]>([]);
  readonly municipalityIds = signal<string[]>([]);
  private readonly saved = signal<Snapshot>({ headline: '', bio: '', categoryIds: [], municipalityIds: [] });

  readonly checklist = computed(() => [
    { label: 'A short headline', done: this.headline().trim().length > 0 },
    { label: 'A few words about you', done: this.bio().trim().length > 0 },
    { label: 'At least one category', done: this.categoryIds().length > 0 },
    { label: 'At least one municipality', done: this.municipalityIds().length > 0 },
  ]);

  readonly completeness = computed(() => {
    const items = this.checklist();
    return Math.round((items.filter((item) => item.done).length / items.length) * 100);
  });

  readonly dirty = computed(() => {
    const saved = this.saved();
    return (
      this.headline().trim() !== saved.headline ||
      this.bio().trim() !== saved.bio ||
      !sameIds(this.categoryIds(), saved.categoryIds) ||
      !sameIds(this.municipalityIds(), saved.municipalityIds)
    );
  });

  constructor(
    private taskerProfileService: TaskerProfileService,
    private referenceService: ReferenceService,
    private toastService: ToastService,
    route: ActivatedRoute,
  ) {
    this.welcome = route.snapshot.queryParamMap.has('welcome');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.failed.set(false);
    forkJoin({
      profile: this.taskerProfileService.getMine(),
      categories: this.referenceService.getCategories(),
      municipalities: this.referenceService.getMunicipalities(),
    }).subscribe({
      next: ({ profile, categories, municipalities }) => {
        this.categories.set(categories);
        this.municipalities.set(municipalities);
        this.apply(profile);
      },
      error: () => this.failed.set(true),
    });
  }

  initials(name: string | undefined): string {
    return (name ?? '')
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }

  toggle(list: 'categoryIds' | 'municipalityIds', id: string | undefined): void {
    if (!id) {
      return;
    }
    this[list].update((ids) => (ids.includes(id) ? ids.filter((current) => current !== id) : [...ids, id]));
  }

  save(): void {
    const saved = this.saved();
    if (this.saving() || !this.dirty()) {
      return;
    }
    const categoriesChanged = !sameIds(this.categoryIds(), saved.categoryIds);
    const municipalitiesChanged = !sameIds(this.municipalityIds(), saved.municipalityIds);
    if ((categoriesChanged && this.categoryIds().length === 0) || (municipalitiesChanged && this.municipalityIds().length === 0)) {
      this.toastService.error('Keep at least one category and one municipality, or clients will not find you.');
      return;
    }

    const requests: Observable<TaskerProfile>[] = [];
    if (this.headline().trim() !== saved.headline || this.bio().trim() !== saved.bio) {
      requests.push(this.taskerProfileService.updateAbout({ headline: this.headline().trim(), bio: this.bio().trim() }));
    }
    if (categoriesChanged) {
      requests.push(this.taskerProfileService.updateCategories(this.categoryIds()));
    }
    if (municipalitiesChanged) {
      requests.push(this.taskerProfileService.updateMunicipalities(this.municipalityIds()));
    }

    this.saving.set(true);
    concat(...requests)
      .pipe(last())
      .subscribe({
        next: (profile) => {
          this.saving.set(false);
          this.apply(profile);
          this.toastService.success('Profile saved.');
        },
        error: (error) => {
          this.saving.set(false);
          this.toastService.error(readApiError(error).message);
        },
      });
  }

  private apply(profile: TaskerProfile): void {
    const snapshot: Snapshot = {
      headline: profile.headline ?? '',
      bio: profile.bio ?? '',
      categoryIds: (profile.categories ?? []).map((category) => category.id!),
      municipalityIds: (profile.municipalities ?? []).map((municipality) => municipality.id!),
    };
    this.profile.set(profile);
    this.saved.set(snapshot);
    this.headline.set(snapshot.headline);
    this.bio.set(snapshot.bio);
    this.categoryIds.set(snapshot.categoryIds);
    this.municipalityIds.set(snapshot.municipalityIds);
  }
}
