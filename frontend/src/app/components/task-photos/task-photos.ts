import { Component, ElementRef, OnChanges, ViewChild, computed, input, signal } from '@angular/core';
import { ChevronLeft, ChevronRight, Images, X } from 'lucide';
import { TaskDetail, TaskPhoto } from '../../api/models';
import { TaskService } from '../../services/task.service';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { MAX_PHOTOS, PhotoPreparer } from '../../shared/photos/photos';
import { readApiError } from '../../shared/api-error';
import { ToastService } from '../../shared/toast/toast.service';
import { Icon } from '../icon/icon';
import { PhotoDropzone } from '../photo-dropzone/photo-dropzone';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-task-photos',
  imports: [Icon, PhotoDropzone, TranslatePipe],
  templateUrl: './task-photos.html',
})
export class TaskPhotos implements OnChanges {
  protected readonly icons = { ChevronLeft, ChevronRight, Images, X };
  readonly max = MAX_PHOTOS;

  readonly task = input.required<TaskDetail>();
  readonly editable = input(false);

  readonly photos = signal<TaskPhoto[]>([]);
  readonly uploading = signal(false);
  readonly viewing = signal<number | null>(null);
  readonly remaining = computed(() => Math.max(0, MAX_PHOTOS - this.photos().length));
  readonly current = computed(() => {
    const index = this.viewing();
    return index === null ? null : this.photos()[index] ?? null;
  });

  @ViewChild('viewer') private viewer?: ElementRef<HTMLDialogElement>;

  constructor(
    private taskService: TaskService,
    private preparer: PhotoPreparer,
    private confirmService: ConfirmService,
    private toastService: ToastService,
  ) {}

  ngOnChanges(): void {
    this.photos.set(this.task().photos ?? []);
  }

  async add(files: File[]): Promise<void> {
    this.uploading.set(true);
    for (const file of files) {
      try {
        const prepared = await this.preparer.prepare(file);
        const photo = await firstValueFrom(this.taskService.uploadPhoto(this.task().id!, prepared));
        this.photos.update((photos) => [...photos, photo]);
      } catch (error) {
        this.toastService.error(readApiError(error).message || t('photos.uploadFailed'));
      }
    }
    this.uploading.set(false);
  }

  async remove(photo: TaskPhoto, event: Event): Promise<void> {
    event.stopPropagation();
    const confirmed = await this.confirmService.ask({
      title: t('photos.removeTitle'),
      message: t('photos.removeText'),
      confirmLabel: t('photos.remove'),
      tone: 'danger',
    });
    if (!confirmed) {
      return;
    }
    try {
      await firstValueFrom(this.taskService.deletePhoto(this.task().id!, photo.id!));
      this.photos.update((photos) => photos.filter((item) => item.id !== photo.id));
    } catch (error) {
      this.toastService.error(readApiError(error).message);
    }
  }

  open(index: number): void {
    this.viewing.set(index);
    const dialog = this.viewer?.nativeElement;
    if (!dialog || dialog.open) {
      return;
    }
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  close(): void {
    const dialog = this.viewer?.nativeElement;
    if (dialog && typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog?.removeAttribute('open');
      this.viewing.set(null);
    }
  }

  step(delta: number): void {
    const count = this.photos().length;
    const index = this.viewing();
    if (index !== null && count > 0) {
      this.viewing.set((index + delta + count) % count);
    }
  }

  onKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowRight') {
      this.step(1);
    } else if (event.key === 'ArrowLeft') {
      this.step(-1);
    }
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === this.viewer?.nativeElement) {
      this.close();
    }
  }
}
