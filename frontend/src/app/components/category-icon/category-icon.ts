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
  type IconNode,
} from 'lucide';
import { Icon } from '../icon/icon';

const ICONS: Record<string, IconNode> = {
  plumbing: Wrench,
  electrical: Zap,
  moving: Truck,
  'furniture-assembly': Hammer,
  cleaning: Sparkles,
  painting: Paintbrush,
  tiling: Grid2x2,
  carpentry: Axe,
  'air-conditioning': Snowflake,
  heating: Flame,
  'appliance-repair': WashingMachine,
  locksmith: KeyRound,
  gardening: Sprout,
  'computer-help': Laptop,
};

@Component({
  selector: 'app-category-icon',
  imports: [Icon],
  template: `<svg [appIcon]="icon" [class]="size"></svg>`,
})
export class CategoryIcon {
  @Input() size = 'w-3.5 h-3.5';
  @Input({ required: true }) slug: string | undefined;

  get icon(): IconNode {
    return ICONS[this.slug ?? ''] ?? Tag;
  }
}
