import { Component, ElementRef, HostListener, ViewChild, effect } from '@angular/core';
import { CircleAlert, CircleHelp } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { ConfirmService } from './confirm.service';
import { TranslatePipe } from '../../i18n/translate.pipe';

@Component({
  selector: 'app-confirm-dialog',
  imports: [Icon, TranslatePipe],
  templateUrl: './confirm-dialog.html',
})
export class ConfirmDialog {
  protected readonly icons = { CircleAlert, CircleHelp };

  @ViewChild('confirmButton') private confirmButton?: ElementRef<HTMLButtonElement>;

  constructor(protected confirmService: ConfirmService) {
    effect(() => {
      if (this.confirmService.pending()) {
        queueMicrotask(() => this.confirmButton?.nativeElement.focus());
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.confirmService.pending()) {
      this.confirmService.answer(false);
    }
  }
}
