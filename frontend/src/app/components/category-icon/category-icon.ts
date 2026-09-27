import { Component, Input } from '@angular/core';
import {
  Axe,
  Flame,
  Grid2x2,
  Hammer,
  KeyRound,
  Laptop,
  Paintbrush,
  Snowflake,
  Sparkles,
  Sprout,
  Tag,
  Truck,
  WashingMachine,
  Wrench,
  Zap,
} from 'lucide';
import { Icon } from '../icon/icon';

type IconKey =
  | 'plumbing'
  | 'electrical'
  | 'moving'
  | 'assembly'
  | 'cleaning'
  | 'painting'
  | 'tiling'
  | 'carpentry'
  | 'cooling'
  | 'heating'
  | 'appliances'
  | 'locks'
  | 'garden'
  | 'computers'
  | 'other';

const KEYWORDS: [string, IconKey][] = [
  ['vodoinstal', 'plumbing'],
  ['elektro', 'electrical'],
  ['selidb', 'moving'],
  ['montaz', 'assembly'],
  ['cisc', 'cleaning'],
  ['moler', 'painting'],
  ['kerami', 'tiling'],
  ['stolar', 'carpentry'],
  ['klima', 'cooling'],
  ['grijanj', 'heating'],
  ['aparat', 'appliances'],
  ['bravar', 'locks'],
  ['vrt', 'garden'],
  ['racunar', 'computers'],
];

@Component({
  selector: 'app-category-icon',
  imports: [Icon],
  template: `
    @switch (key) {
      @case ('plumbing') { <svg [appIcon]="icons.Wrench" [class]="size"></svg> }
      @case ('electrical') { <svg [appIcon]="icons.Zap" [class]="size"></svg> }
      @case ('moving') { <svg [appIcon]="icons.Truck" [class]="size"></svg> }
      @case ('assembly') { <svg [appIcon]="icons.Hammer" [class]="size"></svg> }
      @case ('cleaning') { <svg [appIcon]="icons.Sparkles" [class]="size"></svg> }
      @case ('painting') { <svg [appIcon]="icons.Paintbrush" [class]="size"></svg> }
      @case ('tiling') { <svg [appIcon]="icons.Grid2x2" [class]="size"></svg> }
      @case ('carpentry') { <svg [appIcon]="icons.Axe" [class]="size"></svg> }
      @case ('cooling') { <svg [appIcon]="icons.Snowflake" [class]="size"></svg> }
      @case ('heating') { <svg [appIcon]="icons.Flame" [class]="size"></svg> }
      @case ('appliances') { <svg [appIcon]="icons.WashingMachine" [class]="size"></svg> }
      @case ('locks') { <svg [appIcon]="icons.KeyRound" [class]="size"></svg> }
      @case ('garden') { <svg [appIcon]="icons.Sprout" [class]="size"></svg> }
      @case ('computers') { <svg [appIcon]="icons.Laptop" [class]="size"></svg> }
      @default { <svg [appIcon]="icons.Tag" [class]="size"></svg> }
    }
  `,
})
export class CategoryIcon {
  protected readonly icons = {
    Axe,
    Flame,
    Grid2x2,
    Hammer,
    KeyRound,
    Laptop,
    Paintbrush,
    Snowflake,
    Sparkles,
    Sprout,
    Tag,
    Truck,
    WashingMachine,
    Wrench,
    Zap,
  };

  @Input() size = 'w-3.5 h-3.5';

  key: IconKey = 'other';

  @Input({ required: true }) set name(value: string | undefined) {
    const normalized = (value ?? '').toLowerCase();
    this.key = KEYWORDS.find(([word]) => normalized.includes(word))?.[1] ?? 'other';
  }
}
