import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { ConversationService } from './conversation.service';

describe('ConversationService', () => {
  let service: ConversationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ConversationService);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'token',
      expiresIn: 900,
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles: ['CLIENT'],
    });
    http.expectOne('/api/conversations/unread-count').flush({ count: 5 });
    http.expectOne('/api/notifications/unread-count').flush({ count: 2 });
  });

  afterEach(() => http.verify());

  it('counts unread messages once someone is logged in', () => {
    expect(service.unreadCount()).toBe(5);
  });

  it('asks for a page of messages', () => {
    service.messages('c1', 2).subscribe();

    const request = http.expectOne((req) => req.url === '/api/conversations/c1/messages');
    expect(request.request.params.toString()).toBe('page=2&size=30');
  });

  it('finds the conversation that belongs to an offer', () => {
    service.forOffer('o1').subscribe();

    http.expectOne('/api/conversations/by-offer/o1');
  });

  it('lowers the count by the conversation unread messages and recounts messages and notifications', () => {
    service.markRead({ id: 'c1', unreadCount: 3 }).subscribe();
    expect(service.unreadCount()).toBe(2);

    http.expectOne({ method: 'POST', url: '/api/conversations/c1/read' }).flush(null);
    http.expectOne('/api/conversations/unread-count').flush({ count: 2 });
    http.expectOne('/api/notifications/unread-count').flush({ count: 1 });
    expect(service.unreadCount()).toBe(2);
  });
});
