import { Component } from '@angular/core';
import { CircleCheck, CircleAlert, Info, X } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { ToastService } from './toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';

@Component({
  selector: 'app-toasts',
  imports: [Icon, TranslatePipe],
  templateUrl: './toasts.html',
})
export class Toasts {
  protected readonly icons = { CircleCheck, CircleAlert, Info, X };

  constructor(protected toastService: ToastService) {}
}
