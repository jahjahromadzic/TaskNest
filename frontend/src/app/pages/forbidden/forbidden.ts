import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ShieldAlert } from 'lucide';
import { Icon } from '../../components/icon/icon';
import { TranslatePipe } from '../../i18n/translate.pipe';

@Component({
  selector: 'app-forbidden',
  imports: [Icon, RouterLink, TranslatePipe],
  templateUrl: './forbidden.html',
})
export class Forbidden {
  protected readonly icons = { ShieldAlert };
}
