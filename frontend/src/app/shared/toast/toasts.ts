import { Component } from '@angular/core';
import { LucideCircleCheck, LucideCircleAlert, LucideInfo, LucideX } from '@lucide/angular';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toasts',
  imports: [LucideCircleCheck, LucideCircleAlert, LucideInfo, LucideX],
  templateUrl: './toasts.html',
})
export class Toasts {
  constructor(protected toastService: ToastService) {}
}
