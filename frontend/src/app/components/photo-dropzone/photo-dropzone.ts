import { Component, input, output, signal } from '@angular/core';
import { ImagePlus, LoaderCircle } from 'lucide';
import { Icon } from '../icon/icon';
import { ACCEPTED_TYPES, MAX_PHOTOS, photoProblem } from '../../shared/photos/photos';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t } from '../../i18n/translate';

@Component({
  selector: 'app-photo-dropzone',
  imports: [Icon, TranslatePipe],
  template: `
    <label class="flex flex-col items-center justify-center gap-1.5 p-5 rounded-card border-2 border-dashed text-center transition-all duration-200"
           [class]="dragging() ? 'border-brand bg-brand-50 scale-[1.01]' : 'border-slate-300 hover:border-brand/60 hover:bg-slate-50'"
           [class.opacity-60]="disabled"
           [class.cursor-pointer]="!disabled"
           (dragover)="onDragOver($event)" (dragleave)="dragging.set(false)" (drop)="onDrop($event)">
      @if (busy()) {
        <svg [appIcon]="icons.LoaderCircle" class="w-6 h-6 text-brand animate-spin"></svg>
        <span class="text-sm font-medium">{{ 'photos.uploading' | t }}</span>
      } @else {
        <svg [appIcon]="icons.ImagePlus" class="w-6 h-6 text-brand transition-transform duration-200" [class.scale-110]="dragging()"></svg>
        <span class="text-sm font-medium">{{ (remaining() === 0 ? 'photos.full' : 'photos.drop') | t: { count: max } }}</span>
        <span class="text-xs text-text-sub">{{ 'photos.hint' | t: { count: max } }}</span>
      }
      <input type="file" class="sr-only" multiple [accept]="accept" [disabled]="disabled" (change)="onChange($event)">
    </label>
    @for (error of errors(); track $index) {
      <p class="form-error">{{ error }}</p>
    }
  `,
})
export class PhotoDropzone {
  protected readonly icons = { ImagePlus, LoaderCircle };
  readonly accept = ACCEPTED_TYPES.join(',');
  readonly max = MAX_PHOTOS;

  readonly remaining = input.required<number>();
  readonly busy = input(false);
  readonly picked = output<File[]>();

  readonly dragging = signal(false);
  readonly errors = signal<string[]>([]);

  get disabled(): boolean {
    return this.remaining() === 0 || this.busy();
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (!this.disabled) {
      this.dragging.set(true);
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (!this.disabled) {
      this.take(Array.from(event.dataTransfer?.files ?? []));
    }
  }

  onChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.take(Array.from(input.files ?? []));
    input.value = '';
  }

  take(files: File[]): void {
    const errors: string[] = [];
    const accepted: File[] = [];
    for (const file of files) {
      const problem = photoProblem(file);
      if (problem) {
        errors.push(t(problem === 'unsupported' ? 'photos.unsupported' : 'photos.tooBig', { name: file.name }));
      } else if (accepted.length < this.remaining()) {
        accepted.push(file);
      } else {
        errors.push(t('photos.overLimit', { name: file.name, count: this.max }));
      }
    }
    this.errors.set(errors);
    if (accepted.length > 0) {
      this.picked.emit(accepted);
    }
  }
}
