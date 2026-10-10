import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, input } from '@angular/core';
import * as L from 'leaflet';

export interface MapPoint {
  latitude: number;
  longitude: number;
  label: string;
}

const PIN = L.divIcon({ className: 'map-pin', html: '<span></span>', iconSize: [28, 28], iconAnchor: [14, 28] });

@Component({
  selector: 'app-map-view',
  template: `<div #map class="h-64 w-full rounded-card overflow-hidden border border-surface-border z-0"></div>`,
})
export class MapView implements AfterViewInit, OnDestroy {
  readonly points = input<MapPoint[]>([]);

  @ViewChild('map', { static: true }) private element!: ElementRef<HTMLElement>;
  private map?: L.Map;

  ngAfterViewInit(): void {
    this.map = L.map(this.element.nativeElement, { scrollWheelZoom: false });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap',
    }).addTo(this.map);

    const pins = this.points().map((point) =>
      L.marker([point.latitude, point.longitude], { icon: PIN, title: point.label }).addTo(this.map!),
    );
    if (pins.length === 1) {
      this.map.setView(pins[0].getLatLng(), 16);
    } else if (pins.length > 1) {
      this.map.fitBounds(L.featureGroup(pins).getBounds(), { padding: [32, 32] });
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
