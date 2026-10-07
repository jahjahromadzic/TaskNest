import { Component, OnDestroy, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, concat, defaultIfEmpty, from, map, of, shareReplay, switchMap, toArray } from 'rxjs';
import { ArrowLeft, CircleAlert, Eye, Lightbulb, LoaderCircle, MapPin, Save, Send, X } from 'lucide';
import { Category, Municipality, TaskDetail, TaskSummary } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Icon } from '../../components/icon/icon';
import { PhotoDropzone } from '../../components/photo-dropzone/photo-dropzone';
import { Select, SelectOption } from '../../components/select/select';
import { TaskCard } from '../../components/task-card/task-card';
import { ReferenceService } from '../../services/reference.service';
import { TaskService } from '../../services/task.service';
import { ApiError, readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';
import { MAX_PHOTOS, PhotoPreparer } from '../../shared/photos/photos';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

export const TITLE_MAX = 200;
export const DESCRIPTION_MAX = 5000;

type SubmitMode = 'draft' | 'publish';

interface PendingPhoto {
  file: File;
  preview: string;
}

@Component({
  selector: 'app-post-task',
  imports: [AsyncPipe, FormsModule, RouterLink, CategoryIcon, Icon, PhotoDropzone, Select, TaskCard, TranslatePipe, CategoryPipe],
  templateUrl: './post-task.html',
})
export class PostTask implements OnDestroy {
  protected readonly icons = { ArrowLeft, CircleAlert, Eye, Lightbulb, LoaderCircle, MapPin, Save, Send, X };
  readonly maxPhotos = MAX_PHOTOS;
  readonly photos = signal<PendingPhoto[]>([]);

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
    private preparer: PhotoPreparer,
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
    const category = categories?.find((candidate) => candidate.id === this.categoryId);
    return {
      id: 'preview',
      title: this.title.trim() || t('postTask.previewTitle'),
      budget: this.budget ?? undefined,
      status: 'PUBLISHED',
      categorySlug: category?.slug,
      categoryName: category?.name ?? t('postTask.previewCategory'),
      municipalityName: municipalities?.find((item) => item.id === this.municipalityId)?.name ?? t('postTask.previewMunicipality'),
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

  addPhotos(files: File[]): void {
    this.photos.update((photos) => [...photos, ...files.map((file) => ({ file, preview: URL.createObjectURL(file) }))]);
  }

  removePhoto(index: number): void {
    const photo = this.photos()[index];
    URL.revokeObjectURL(photo.preview);
    this.photos.update((photos) => photos.filter((_, i) => i !== index));
  }

  ngOnDestroy(): void {
    this.photos().forEach((photo) => URL.revokeObjectURL(photo.preview));
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
        switchMap((draft) => this.uploadPhotos(draft.id!).pipe(map(() => draft))),
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

  private uploadPhotos(taskId: string): Observable<number> {
    const uploads = this.photos().map((photo) =>
      from(this.preparer.prepare(photo.file)).pipe(
        switchMap((prepared) => this.taskService.uploadPhoto(taskId, prepared)),
        map(() => true),
        catchError(() => of(false)),
      ),
    );
    return concat(...uploads).pipe(
      toArray(),
      defaultIfEmpty([] as boolean[]),
      map((results) => results.filter((ok) => !ok).length),
      map((failed) => {
        if (failed > 0) {
          this.toastService.error(t('photos.someFailed', { count: failed }));
        }
        return failed;
      }),
    );
  }

  private finish(task: TaskDetail, published: boolean, publishFailed: boolean): void {
    if (publishFailed) {
      this.toastService.error(t('postTask.publishFailed'));
    } else if (published) {
      this.toastService.success(t('postTask.published'));
    } else {
      this.toastService.success(t('postTask.draftSaved'));
    }
    this.router.navigate(['/tasks', task.id]);
  }
}
