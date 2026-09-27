import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideMapPinOff } from '@lucide/angular';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, LucideMapPinOff],
  templateUrl: './not-found.html',
})
export class NotFound {}
