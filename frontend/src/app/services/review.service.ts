import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateReviewRequest, Review, ReviewPage } from '../api/models';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(private http: HttpClient) {}

  getTaskReviews(taskId: string): Observable<Review[]> {
    return this.http.get<Review[]>(`/api/tasks/${encodeURIComponent(taskId)}/reviews`);
  }

  getReceived(userId: string, page: number, size = 10): Observable<ReviewPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ReviewPage>(`/api/users/${encodeURIComponent(userId)}/reviews`, { params });
  }

  create(taskId: string, request: CreateReviewRequest): Observable<Review> {
    return this.http.post<Review>(`/api/tasks/${encodeURIComponent(taskId)}/reviews`, request);
  }
}
