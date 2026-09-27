import { Component, Input } from '@angular/core';
import {
  LucideHammer,
  LucidePaintbrush,
  LucideSparkles,
  LucideTag,
  LucideTruck,
  LucideWrench,
  LucideZap,
} from '@lucide/angular';

type IconKey = 'plumbing' | 'electrical' | 'moving' | 'assembly' | 'cleaning' | 'painting' | 'other';

const KEYWORDS: [string, IconKey][] = [
  ['vodoinstal', 'plumbing'],
  ['elektro', 'electrical'],
  ['selidb', 'moving'],
  ['montaz', 'assembly'],
  ['namjest', 'assembly'],
  ['cisc', 'cleaning'],
  ['moler', 'painting'],
  ['krec', 'painting'],
];

@Component({
  selector: 'app-category-icon',
  imports: [LucideHammer, LucidePaintbrush, LucideSparkles, LucideTag, LucideTruck, LucideWrench, LucideZap],
  template: `
    @switch (key) {
      @case ('plumbing') { <svg lucideWrench [class]="size"></svg> }
      @case ('electrical') { <svg lucideZap [class]="size"></svg> }
      @case ('moving') { <svg lucideTruck [class]="size"></svg> }
      @case ('assembly') { <svg lucideHammer [class]="size"></svg> }
      @case ('cleaning') { <svg lucideSparkles [class]="size"></svg> }
      @case ('painting') { <svg lucidePaintbrush [class]="size"></svg> }
      @default { <svg lucideTag [class]="size"></svg> }
    }
  `,
})
export class CategoryIcon {
  @Input() size = 'w-3.5 h-3.5';

  key: IconKey = 'other';

  @Input({ required: true }) set name(value: string | undefined) {
    const normalized = (value ?? '').toLowerCase();
    this.key = KEYWORDS.find(([word]) => normalized.includes(word))?.[1] ?? 'other';
  }
}
