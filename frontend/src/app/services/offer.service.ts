import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateOfferRequest, Offer, TaskOffer } from '../api/models';

@Injectable({ providedIn: 'root' })
export class OfferService {
  constructor(private http: HttpClient) {}

  getTaskOffers(taskId: string): Observable<TaskOffer[]> {
    return this.http.get<TaskOffer[]>(`/api/tasks/${encodeURIComponent(taskId)}/offers`);
  }

  getMyOfferForTask(taskId: string): Observable<Offer | null> {
    return this.http.get<Offer | null>(`/api/tasks/${encodeURIComponent(taskId)}/offers/mine`);
  }

  submit(taskId: string, request: CreateOfferRequest): Observable<Offer> {
    return this.http.post<Offer>(`/api/tasks/${encodeURIComponent(taskId)}/offers`, request);
  }

  accept(offerId: string): Observable<unknown> {
    return this.http.post(`/api/offers/${encodeURIComponent(offerId)}/accept`, null);
  }

  withdraw(offerId: string): Observable<Offer> {
    return this.http.post<Offer>(`/api/offers/${encodeURIComponent(offerId)}/withdraw`, null);
  }
}
