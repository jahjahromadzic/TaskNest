import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MapPinOff } from 'lucide';
import { Icon } from '../../components/icon/icon';

@Component({
  selector: 'app-not-found',
  imports: [Icon, RouterLink],
  templateUrl: './not-found.html',
})
export class NotFound {
  protected readonly icons = { MapPinOff };
}
