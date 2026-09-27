import { Component, Input } from '@angular/core';
import type { IconNode } from 'lucide';

@Component({
  selector: 'svg[appIcon]',
  template: `
    @for (child of appIcon; track $index) {
      @let attrs = child[1];
      @switch (child[0]) {
        @case ('path') {
          <svg:path [attr.d]="attrs['d']" />
        }
        @case ('circle') {
          <svg:circle [attr.cx]="attrs['cx']" [attr.cy]="attrs['cy']" [attr.r]="attrs['r']" />
        }
        @case ('rect') {
          <svg:rect [attr.x]="attrs['x']" [attr.y]="attrs['y']" [attr.width]="attrs['width']"
                    [attr.height]="attrs['height']" [attr.rx]="attrs['rx']" [attr.ry]="attrs['ry']" />
        }
        @case ('line') {
          <svg:line [attr.x1]="attrs['x1']" [attr.y1]="attrs['y1']" [attr.x2]="attrs['x2']" [attr.y2]="attrs['y2']" />
        }
        @case ('ellipse') {
          <svg:ellipse [attr.cx]="attrs['cx']" [attr.cy]="attrs['cy']" [attr.rx]="attrs['rx']" [attr.ry]="attrs['ry']" />
        }
        @case ('polyline') {
          <svg:polyline [attr.points]="attrs['points']" />
        }
        @case ('polygon') {
          <svg:polygon [attr.points]="attrs['points']" />
        }
      }
    }
  `,
  host: {
    xmlns: 'http://www.w3.org/2000/svg',
    width: '24',
    height: '24',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    'stroke-width': '2',
    'stroke-linecap': 'round',
    'stroke-linejoin': 'round',
    'aria-hidden': 'true',
  },
})
export class Icon {
  @Input({ required: true }) appIcon!: IconNode;
}
