import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TaskOffer } from '../api/models';

@Injectable({ providedIn: 'root' })
export class OfferService {
  constructor(private http: HttpClient) {}

  getTaskOffers(taskId: string): Observable<TaskOffer[]> {
    return this.http.get<TaskOffer[]>(`/api/tasks/${encodeURIComponent(taskId)}/offers`);
  }

  accept(offerId: string): Observable<unknown> {
    return this.http.post(`/api/offers/${encodeURIComponent(offerId)}/accept`, null);
  }
}
