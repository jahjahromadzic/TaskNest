import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateReviewRequest, Review } from '../api/models';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(private http: HttpClient) {}

  getTaskReviews(taskId: string): Observable<Review[]> {
    return this.http.get<Review[]>(`/api/tasks/${encodeURIComponent(taskId)}/reviews`);
  }

  create(taskId: string, request: CreateReviewRequest): Observable<Review> {
    return this.http.post<Review>(`/api/tasks/${encodeURIComponent(taskId)}/reviews`, request);
  }
}
