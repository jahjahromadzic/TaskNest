import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideShieldAlert } from '@lucide/angular';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, LucideShieldAlert],
  templateUrl: './forbidden.html',
})
export class Forbidden {}
