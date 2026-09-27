import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Construction } from 'lucide';
import { Icon } from '../../components/icon/icon';

@Component({
  selector: 'app-placeholder',
  imports: [Icon],
  templateUrl: './placeholder.html',
})
export class Placeholder {
  protected readonly icons = { Construction };

  readonly title: string;
  readonly phase: number;

  constructor(route: ActivatedRoute) {
    this.title = route.snapshot.data['heading'];
    this.phase = route.snapshot.data['phase'];
  }
}
