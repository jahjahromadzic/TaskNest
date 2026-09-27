import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AppNotification, ChatMessage, Conversation } from '../../api/models';
import { Subject } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { LiveMessage, LiveRead, RealtimeService } from '../../services/realtime.service';

describe('Messages page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let live: {
    connected: ReturnType<typeof signal<boolean>>;
    connected$: Subject<void>;
    messages$: Subject<LiveMessage>;
    reads$: Subject<LiveRead>;
    notifications$: Subject<AppNotification>;
  };

  const withEmir: Conversation = {
    id: 'c1',
    offerId: 'o1',
    offerPrice: 85,
    offerMessage: 'I have both parts in stock',
    taskId: 't1',
    taskTitle: 'Replace the sink trap',
    taskStatus: 'ASSIGNED',
    otherPartyId: 'emir',
    otherPartyName: 'Emir Kovačević',
    status: 'OPEN',
    lastMessageAt: '2026-09-27T10:00:00',
    lastMessage: 'See you on Thursday',
    lastMessageSenderId: 'emir',
    unreadCount: 2,
  };
  const withSelma: Conversation = {
    id: 'c2',
    offerId: 'o2',
    offerPrice: 60,
    taskId: 't2',
    taskTitle: 'Clean the flat',
    taskStatus: 'PUBLISHED',
    otherPartyId: 'selma',
    otherPartyName: 'Selma Karić',
    status: 'ARCHIVED',
    lastMessage: 'Thanks anyway',
    lastMessageSenderId: 'u1',
    unreadCount: 0,
  };
  const hello: ChatMessage = { id: 'm1', senderId: 'u1', content: 'Hello Emir', createdAt: '2026-09-27T09:00:00', readAt: '2026-09-27T09:05:00' };
  const answer: ChatMessage = { id: 'm2', senderId: 'emir', content: 'See you on Thursday', createdAt: '2026-09-27T10:00:00' };

  beforeAll(async () => {
    await import('./messages');
  }, 60_000);

  beforeEach(async () => {
    live = {
      connected: signal(false),
      connected$: new Subject(),
      messages$: new Subject(),
      reads$: new Subject(),
      notifications$: new Subject(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: live },
      ],
    });
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
    harness = await RouterTestingHarness.create();
  });

  function answerCounts(): void {
    http
      .match((request) => request.url.endsWith('/unread-count'))
      .filter((request) => !request.cancelled)
      .forEach((request) => request.flush({ count: 0 }));
  }

  function conversationList(): TestRequest {
    return http.expectOne((request) => request.url === '/api/conversations');
  }

  function messagesOf(id: string, page = 0): TestRequest {
    return http.expectOne(
      (request) => request.url === `/api/conversations/${id}/messages` && request.params.get('page') === String(page),
    );
  }

  async function open(url: string, conversations = [withEmir, withSelma]): Promise<void> {
    await harness.navigateByUrl(url);
    conversationList().flush({ content: conversations, page: 0, size: 50, totalElements: conversations.length, totalPages: 1 });
    answerCounts();
    await harness.fixture.whenStable();
  }

  async function openThread(messages: ChatMessage[] = [hello, answer], total = messages.length): Promise<void> {
    await open('/messages?conversation=c1');
    messagesOf('c1').flush({ content: messages, page: 0, size: 30, totalElements: total, totalPages: 1 });
    http.expectOne({ method: 'POST', url: '/api/conversations/c1/read' }).flush(null);
    answerCounts();
    await harness.fixture.whenStable();
  }

  function element(): HTMLElement {
    return harness.routeNativeElement!;
  }

  function text(): string {
    return (element().textContent ?? '').replace(/\s+/g, ' ');
  }

  function rows(): string[] {
    return Array.from<HTMLElement>(element().querySelectorAll('aside a')).map((row) => row.textContent!.replace(/\s+/g, ' ').trim());
  }

  async function type(value: string): Promise<HTMLTextAreaElement> {
    const composer = element().querySelector('textarea')!;
    composer.value = value;
    composer.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
    return composer;
  }

  async function pressEnter(composer: HTMLTextAreaElement, shiftKey = false): Promise<void> {
    composer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey, cancelable: true }));
    await harness.fixture.whenStable();
  }

  it('lists conversations with a preview, marks your own last message and shows unread counts', async () => {
    await open('/messages');

    expect(rows()[0]).toContain('Emir Kovačević');
    expect(rows()[0]).toContain('See you on Thursday');
    expect(rows()[0]).toMatch(/2$/);
    expect(rows()[1]).toContain('You: Thanks anyway');
    expect(text()).toContain('Pick a conversation');
  });

  it('filters conversations by name or task', async () => {
    await open('/messages');

    const search = element().querySelector<HTMLInputElement>('input[type=search]')!;
    search.value = 'flat';
    search.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();

    expect(rows().length).toBe(1);
    expect(rows()[0]).toContain('Selma Karić');
  });

  it('opens a conversation, shows the offer and the messages, and marks it read', async () => {
    await openThread();

    expect(text()).toContain('Offer · 85 KM');
    expect(text()).toContain('"I have both parts in stock"');
    expect(text()).toContain('Hello Emir');
    expect(element().querySelector('[aria-label=Read]')).not.toBeNull();
    expect(rows()[0]).not.toMatch(/2$/);
  });

  it('sends with Enter, shows the message at once and moves the conversation to the top', async () => {
    await open('/messages?conversation=c1', [{ ...withSelma, status: 'OPEN' }, withEmir]);
    messagesOf('c1').flush({ content: [hello, answer], page: 0, size: 30, totalElements: 2, totalPages: 1 });
    http.expectOne('/api/conversations/c1/read').flush(null);
    answerCounts();
    await harness.fixture.whenStable();
    expect(rows()[0]).toContain('Selma Karić');

    const composer = await type('Can you bring a new valve?');
    await pressEnter(composer, true);
    http.expectNone((request) => request.method === 'POST' && request.url.endsWith('/messages'));

    await pressEnter(composer);
    expect(text()).toContain('Sending...');
    expect(composer.value).toBe('');

    const request = http.expectOne({ method: 'POST', url: '/api/conversations/c1/messages' });
    expect(request.request.body).toEqual({ content: 'Can you bring a new valve?' });
    request.flush({ id: 'm3', senderId: 'u1', content: 'Can you bring a new valve?', createdAt: '2026-09-27T11:00:00' });
    await harness.fixture.whenStable();

    expect(text()).not.toContain('Sending...');
    expect(rows()[0]).toContain('You: Can you bring a new valve?');
  });

  it('keeps a message that could not be sent and sends it again on retry', async () => {
    await openThread();

    await pressEnter(await type('Are you still coming?'));
    http
      .expectOne({ method: 'POST', url: '/api/conversations/c1/messages' })
      .flush({ detail: 'Server error' }, { status: 500, statusText: 'Server error' });
    await harness.fixture.whenStable();

    expect(text()).toContain('Not sent');
    Array.from<HTMLButtonElement>(element().querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Retry'))!
      .click();
    http
      .expectOne({ method: 'POST', url: '/api/conversations/c1/messages' })
      .flush({ id: 'm3', senderId: 'u1', content: 'Are you still coming?', createdAt: '2026-09-27T11:00:00' });
    await harness.fixture.whenStable();

    expect(text()).not.toContain('Not sent');
    expect(element().querySelectorAll('ol li').length).toBeGreaterThan(2);
  });

  it('keeps an archived conversation readable but closed for new messages', async () => {
    await open('/messages?conversation=c2');
    messagesOf('c2').flush({ content: [hello], page: 0, size: 30, totalElements: 1, totalPages: 1 });
    http.expectOne('/api/conversations/c2/read').flush(null);
    answerCounts();
    await harness.fixture.whenStable();

    expect(text()).toContain('This conversation is archived');
    expect(element().querySelector('textarea')!.disabled).toBe(true);
    expect(element().querySelector<HTMLButtonElement>('button[aria-label=Send]')!.disabled).toBe(true);
  });

  it('loads earlier messages above the ones already shown', async () => {
    await openThread([answer], 2);

    Array.from<HTMLButtonElement>(element().querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Load earlier messages'))!
      .click();
    messagesOf('c1', 1).flush({ content: [hello, answer], page: 1, size: 30, totalElements: 2, totalPages: 1 });
    await harness.fixture.whenStable();

    const bubbles = Array.from(element().querySelectorAll('ol li')).map((item) => item.textContent!.trim());
    expect(bubbles.filter((bubble) => bubble.includes('Hello Emir')).length).toBe(1);
    expect(bubbles.findIndex((bubble) => bubble.includes('Hello Emir'))).toBeLessThan(
      bubbles.findIndex((bubble) => bubble.includes('See you on Thursday')),
    );
    expect(text()).not.toContain('Load earlier messages');
  });

  it('opens the conversation of an offer and puts it in the address', async () => {
    await open('/messages?offer=o1');
    http.expectOne('/api/conversations/by-offer/o1').flush(withEmir);
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/messages?conversation=c1');
    messagesOf('c1').flush({ content: [], page: 0, size: 30, totalElements: 0, totalPages: 0 });
    http.expectOne('/api/conversations/c1/read').flush(null);
    answerCounts();
    await harness.fixture.whenStable();

    expect(text()).toContain('No messages yet. Say hello');
  });

  it('adds a live message to the open conversation and marks it read straight away', async () => {
    await openThread();

    live.messages$.next({
      conversationId: 'c1',
      message: { id: 'm9', senderId: 'emir', content: 'I am on my way', createdAt: '2026-09-27T12:00:00' },
    });
    await harness.fixture.whenStable();

    expect(text()).toContain('I am on my way');
    http.expectOne({ method: 'POST', url: '/api/conversations/c1/read' }).flush(null);
    answerCounts();
    expect(rows()[0]).toContain('I am on my way');
    expect(rows()[0]).not.toMatch(/\d$/);
  });

  it('raises the unread count of another conversation and moves it to the top', async () => {
    await openThread();

    live.messages$.next({
      conversationId: 'c2',
      message: { id: 'm9', senderId: 'selma', content: 'Are you there?', createdAt: '2026-09-27T12:00:00' },
    });
    answerCounts();
    await harness.fixture.whenStable();

    expect(rows()[0]).toContain('Selma Karić');
    expect(rows()[0]).toMatch(/Are you there\? 1$/);
    expect(element().querySelector('ol')!.textContent).not.toContain('Are you there?');
    http.expectNone('/api/conversations/c2/read');
  });

  it('turns the ticks green when the other person reads the conversation', async () => {
    const unread: ChatMessage = { ...hello, readAt: undefined };
    await openThread([unread]);
    expect(element().querySelector('[aria-label=Read]')).toBeNull();

    live.reads$.next({ conversationId: 'c1', readerId: 'emir', readAt: '2026-09-27T12:00:00' });
    await harness.fixture.whenStable();

    expect(element().querySelector('[aria-label=Read]')).not.toBeNull();
  });

  it('shows a sent message once when its live copy arrives before the reply', async () => {
    await openThread();

    await pressEnter(await type('On my way'));
    const saved = { id: 'm9', senderId: 'u1', content: 'On my way', createdAt: '2026-09-27T12:00:00' };
    live.messages$.next({ conversationId: 'c1', message: saved });
    http.expectOne({ method: 'POST', url: '/api/conversations/c1/messages' }).flush(saved);
    await harness.fixture.whenStable();

    const bubbles = Array.from(element().querySelectorAll('ol li')).filter((item) => item.textContent?.includes('On my way'));
    expect(bubbles.length).toBe(1);
    expect(text()).not.toContain('Sending...');
  });

  it('shows whether new messages arrive live', async () => {
    await open('/messages');
    expect(text()).toContain('Offline');

    live.connected.set(true);
    await harness.fixture.whenStable();

    expect(text()).toContain('Live');
  });
});
