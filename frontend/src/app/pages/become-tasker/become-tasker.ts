import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ArrowDown, ArrowRight, Circle, CircleCheck, LoaderCircle, ShieldCheck, Star, Target, Wallet } from 'lucide';
import { Category, Municipality } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { TaskerProfileFields } from '../../components/tasker-profile-fields/tasker-profile-fields';
import { ReferenceService } from '../../services/reference.service';
import { readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t, translated } from '../../i18n/translate';

@Component({
  selector: 'app-become-tasker',
  imports: [FormsModule, Icon, TaskerProfileFields, TranslatePipe],
  templateUrl: './become-tasker.html',
})
export class BecomeTasker implements OnInit {
  protected readonly icons = { ArrowDown, ArrowRight, Circle, CircleCheck, LoaderCircle, ShieldCheck, Star, Target, Wallet };

  readonly categories = signal<Category[]>([]);
  readonly municipalities = signal<Municipality[]>([]);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);

  readonly headline = signal('');
  readonly bio = signal('');
  readonly categoryIds = signal<string[]>([]);
  readonly municipalityIds = signal<string[]>([]);

  readonly benefits = [
    translated({ icon: Target }, { title: 'becomeTasker.benefit1Title', text: 'becomeTasker.benefit1Text' }),
    translated({ icon: Wallet }, { title: 'becomeTasker.benefit2Title', text: 'becomeTasker.benefit2Text' }),
    translated({ icon: Star }, { title: 'becomeTasker.benefit3Title', text: 'becomeTasker.benefit3Text' }),
  ];

  readonly checklist = computed(() => [
    translated({ done: this.headline().trim().length > 0 }, { label: 'profile.checkHeadline' }),
    translated({ done: this.categoryIds().length > 0 }, { label: 'profile.checkCategory' }),
    translated({ done: this.municipalityIds().length > 0 }, { label: 'profile.checkMunicipality' }),
  ]);

  readonly ready = computed(() => this.checklist().every((item) => item.done));

  constructor(
    private authService: AuthService,
    private referenceService: ReferenceService,
    private toastService: ToastService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loadFailed.set(false);
    forkJoin({
      categories: this.referenceService.getCategories(),
      municipalities: this.referenceService.getMunicipalities(),
    }).subscribe({
      next: ({ categories, municipalities }) => {
        this.categories.set(categories);
        this.municipalities.set(municipalities);
      },
      error: () => this.loadFailed.set(true),
    });
  }

  scrollToSetup(): void {
    document.getElementById('tasker-setup')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  submit(): void {
    if (this.saving()) {
      return;
    }
    if (!this.ready()) {
      this.toastService.error(t('becomeTasker.fillRequired'));
      return;
    }
    this.saving.set(true);
    this.authService
      .becomeTasker({
        headline: this.headline().trim(),
        bio: this.bio().trim(),
        categoryIds: this.categoryIds(),
        municipalityIds: this.municipalityIds(),
      })
      .subscribe({
        next: () => {
          this.toastService.success(t('becomeTasker.welcome'));
          this.router.navigate(['/tasker']);
        },
        error: (error) => {
          this.saving.set(false);
          this.toastService.error(readApiError(error).message);
        },
      });
  }
}
