import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LucideChevronLeft, LucideChevronRight } from '@lucide/angular';

export function visiblePages(current: number, total: number): (number | null)[] {
  const pages: (number | null)[] = [];
  for (let page = 0; page < total; page++) {
    const nearCurrent = Math.abs(page - current) <= 1;
    if (page === 0 || page === total - 1 || nearCurrent) {
      pages.push(page);
    } else if (pages[pages.length - 1] !== null) {
      pages.push(null);
    }
  }
  return pages;
}

@Component({
  selector: 'app-pagination',
  imports: [LucideChevronLeft, LucideChevronRight],
  templateUrl: './pagination.html',
})
export class Pagination {
  @Input({ required: true }) page = 0;
  @Input({ required: true }) totalPages = 0;
  @Output() pageChange = new EventEmitter<number>();

  get pages(): (number | null)[] {
    return visiblePages(this.page, this.totalPages);
  }

  go(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.page) {
      this.pageChange.emit(page);
    }
  }
}
