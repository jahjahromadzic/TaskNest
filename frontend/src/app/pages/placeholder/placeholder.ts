import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LucideConstruction } from '@lucide/angular';

@Component({
  selector: 'app-placeholder',
  imports: [LucideConstruction],
  templateUrl: './placeholder.html',
})
export class Placeholder {
  readonly title: string;
  readonly phase: number;

  constructor(route: ActivatedRoute) {
    this.title = route.snapshot.data['heading'];
    this.phase = route.snapshot.data['phase'];
  }
}
