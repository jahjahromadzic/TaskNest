import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ShieldAlert } from 'lucide';
import { Icon } from '../../components/icon/icon';

@Component({
  selector: 'app-forbidden',
  imports: [Icon, RouterLink],
  templateUrl: './forbidden.html',
})
export class Forbidden {
  protected readonly icons = { ShieldAlert };
}
