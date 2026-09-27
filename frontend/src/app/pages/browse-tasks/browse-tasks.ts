import { Component } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { LucidePlus } from '@lucide/angular';
import { ReferenceService } from '../../services/reference.service';

@Component({
  selector: 'app-browse-tasks',
  imports: [AsyncPipe, LucidePlus],
  templateUrl: './browse-tasks.html',
})
export class BrowseTasks {
  readonly categories$;
  readonly skeletonWidths = [96, 72, 120, 84, 104, 68, 112, 90];

  constructor(private referenceService: ReferenceService) {
    this.categories$ = this.referenceService.getCategories();
  }
}
