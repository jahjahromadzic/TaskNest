import { Injectable, Signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ChatMessage, ChatMessagePage, Conversation, ConversationPage } from '../api/models';
import { AuthService } from '../auth/auth.service';
import { UnreadCounter } from '../shared/unread-counter/unread-counter';
import { NotificationService } from './notification.service';

@Injectable({ providedIn: 'root' })
export class ConversationService {
  readonly unreadCount: Signal<number>;

  private readonly counter: UnreadCounter;

  constructor(
    private http: HttpClient,
    private notificationService: NotificationService,
    router: Router,
    authService: AuthService,
  ) {
    this.counter = new UnreadCounter(http, router, authService.user$, '/api/conversations/unread-count');
    this.unreadCount = this.counter.count.asReadonly();
  }

  list(page = 0, size = 50): Observable<ConversationPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ConversationPage>('/api/conversations', { params });
  }

  forOffer(offerId: string): Observable<Conversation> {
    return this.http.get<Conversation>(`/api/conversations/by-offer/${encodeURIComponent(offerId)}`);
  }

  messages(conversationId: string, page: number, size = 30): Observable<ChatMessagePage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ChatMessagePage>(`/api/conversations/${encodeURIComponent(conversationId)}/messages`, { params });
  }

  send(conversationId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`/api/conversations/${encodeURIComponent(conversationId)}/messages`, { content });
  }

  markRead(conversation: Conversation): Observable<void> {
    this.counter.lower(conversation.unreadCount ?? 0);
    return this.http.post<void>(`/api/conversations/${encodeURIComponent(conversation.id ?? '')}/read`, null).pipe(
      tap({
        finalize: () => {
          this.counter.recount();
          this.notificationService.recount();
        },
      }),
    );
  }
}
