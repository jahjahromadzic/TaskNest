import { Component, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, map, of, shareReplay, switchMap } from 'rxjs';
import { ArrowLeft, CircleAlert, Eye, Lightbulb, LoaderCircle, MapPin, Save, Send } from 'lucide';
import { Category, Municipality, TaskDetail, TaskSummary } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { Select, SelectOption } from '../../components/select/select';
import { TaskCard } from '../../components/task-card/task-card';
import { ReferenceService } from '../../services/reference.service';
import { TaskService } from '../../services/task.service';
import { ApiError, readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';

export const TITLE_MAX = 200;
export const DESCRIPTION_MAX = 5000;

type SubmitMode = 'draft' | 'publish';

@Component({
  selector: 'app-post-task',
  imports: [AsyncPipe, FormsModule, RouterLink, CategoryIcon, Icon, Select, TaskCard],
  templateUrl: './post-task.html',
})
export class PostTask {
  protected readonly icons = { ArrowLeft, CircleAlert, Eye, Lightbulb, LoaderCircle, MapPin, Save, Send };

  readonly titleMax = TITLE_MAX;
  readonly descriptionMax = DESCRIPTION_MAX;

  title = '';
  description = '';
  categoryId: string | null = null;
  municipalityId: string | null = null;
  budget: number | null = null;

  readonly attempted = signal(false);
  readonly submitting = signal<SubmitMode | null>(null);
  readonly error = signal<ApiError | null>(null);

  readonly categories$: Observable<Category[]>;
  readonly municipalities$: Observable<Municipality[]>;
  readonly municipalityOptions$: Observable<SelectOption[]>;

  private readonly previewExpiry = new Date(Date.now() + 30 * 86_400_000).toISOString();
  private readonly previewPublished = new Date().toISOString();

  constructor(
    private taskService: TaskService,
    private referenceService: ReferenceService,
    private toastService: ToastService,
    private router: Router,
  ) {
    this.categories$ = this.referenceService.getCategories().pipe(shareReplay(1));
    this.municipalities$ = this.referenceService.getMunicipalities().pipe(shareReplay(1));
    this.municipalityOptions$ = this.municipalities$.pipe(
      map((municipalities) =>
        municipalities.map((municipality) => ({ value: municipality.id ?? '', label: municipality.name ?? '' })),
      ),
    );
  }

  preview(categories: Category[] | null, municipalities: Municipality[] | null): TaskSummary {
    return {
      id: 'preview',
      title: this.title.trim() || 'Your task title',
      budget: this.budget ?? undefined,
      status: 'PUBLISHED',
      categoryName: categories?.find((category) => category.id === this.categoryId)?.name ?? 'Category',
      municipalityName: municipalities?.find((item) => item.id === this.municipalityId)?.name ?? 'Municipality',
      publishedAt: this.previewPublished,
      expiresAt: this.previewExpiry,
    };
  }

  serverError(field: string): string | undefined {
    return this.error()?.fieldErrors[field];
  }

  clearServerError(field: string): void {
    const current = this.error();
    if (current?.fieldErrors[field]) {
      const fieldErrors = { ...current.fieldErrors };
      delete fieldErrors[field];
      this.error.set({ ...current, fieldErrors });
    }
  }

  submit(form: NgForm, mode: SubmitMode): void {
    this.attempted.set(true);
    if (form.invalid || !this.categoryId || this.submitting()) {
      return;
    }

    this.submitting.set(mode);
    this.error.set(null);

    this.taskService
      .createTask({
        title: this.title.trim(),
        description: this.description.trim() || undefined,
        categoryId: this.categoryId,
        municipalityId: this.municipalityId!,
        budget: this.budget ?? undefined,
      })
      .pipe(
        switchMap((draft) =>
          mode === 'draft'
            ? of({ task: draft, published: false })
            : this.taskService.publishTask(draft.id!).pipe(
                map((task) => ({ task, published: true })),
                catchError(() => of({ task: draft, published: false, publishFailed: true })),
              ),
        ),
      )
      .subscribe({
        next: (result) => this.finish(result.task, result.published, 'publishFailed' in result),
        error: (error) => {
          this.error.set(readApiError(error));
          this.submitting.set(null);
        },
      });
  }

  private finish(task: TaskDetail, published: boolean, publishFailed: boolean): void {
    if (publishFailed) {
      this.toastService.error('Your task was saved as a draft, but publishing failed. Try publishing it again.');
    } else if (published) {
      this.toastService.success('Your task is live. Taskers nearby can now send offers.');
    } else {
      this.toastService.success('Draft saved. Publish it whenever you are ready.');
    }
    this.router.navigate(['/tasks', task.id]);
  }
}
