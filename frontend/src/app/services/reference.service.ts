import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category, Municipality } from '../api/models';

@Injectable({ providedIn: 'root' })
export class ReferenceService {
  constructor(private http: HttpClient) {}

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  getMunicipalities(): Observable<Municipality[]> {
    return this.http.get<Municipality[]>('/api/municipalities');
  }
}
