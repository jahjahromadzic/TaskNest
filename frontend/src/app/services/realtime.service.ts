import { Inject, Injectable, InjectionToken, signal } from '@angular/core';
import { Client, IMessage, StompConfig } from '@stomp/stompjs';
import { Observable, Subject, distinctUntilChanged, firstValueFrom, map } from 'rxjs';
import { AppNotification, ChatMessage } from '../api/models';
import { AuthService } from '../auth/auth.service';

export interface LiveMessage {
  conversationId: string;
  message: ChatMessage;
}

export interface LiveRead {
  conversationId: string;
  readerId: string;
  readAt: string;
}

export const RECONNECT_DELAY_MS = 5_000;
export const HEARTBEAT_MS = 10_000;

export type StompClient = Pick<Client, 'activate' | 'deactivate' | 'subscribe'>;

export const STOMP_CLIENT_FACTORY = new InjectionToken<(config: StompConfig) => StompClient>('STOMP_CLIENT_FACTORY', {
  providedIn: 'root',
  factory: () => (config) => new Client(config),
});

export function socketUrl(location: Location = window.location): string {
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;
}

@Injectable({ providedIn: 'root' })
export class RealtimeService {
  readonly connected = signal(false);

  private readonly connectedSubject = new Subject<void>();
  private readonly messagesSubject = new Subject<LiveMessage>();
  private readonly readsSubject = new Subject<LiveRead>();
  private readonly notificationsSubject = new Subject<AppNotification>();

  readonly connected$: Observable<void> = this.connectedSubject.asObservable();
  readonly messages$: Observable<LiveMessage> = this.messagesSubject.asObservable();
  readonly reads$: Observable<LiveRead> = this.readsSubject.asObservable();
  readonly notifications$: Observable<AppNotification> = this.notificationsSubject.asObservable();

  private client: StompClient | null = null;
  private started = false;

  constructor(
    private authService: AuthService,
    @Inject(STOMP_CLIENT_FACTORY) private createClient: (config: StompConfig) => StompClient,
  ) {}

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.authService.user$
      .pipe(
        map((user) => user?.id ?? null),
        distinctUntilChanged(),
      )
      .subscribe((userId) => (userId ? this.connect() : this.disconnect()));
  }

  private connect(): void {
    this.disconnect();
    const client = this.createClient({
      brokerURL: socketUrl(),
      reconnectDelay: RECONNECT_DELAY_MS,
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      beforeConnect: async (stomp) => {
        stomp.connectHeaders = { Authorization: `Bearer ${await this.freshToken()}` };
      },
      onConnect: () => {
        client.subscribe('/user/queue/messages', (frame) => this.messagesSubject.next(parse<LiveMessage>(frame)));
        client.subscribe('/user/queue/reads', (frame) => this.readsSubject.next(parse<LiveRead>(frame)));
        client.subscribe('/user/queue/notifications', (frame) =>
          this.notificationsSubject.next(parse<AppNotification>(frame)),
        );
        this.connected.set(true);
        this.connectedSubject.next();
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    client.activate();
    this.client = client;
  }

  private disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
  }

  private async freshToken(): Promise<string> {
    if (!this.authService.token || this.authService.tokenExpiresSoon) {
      try {
        await firstValueFrom(this.authService.refresh());
      } catch {
        return '';
      }
    }
    return this.authService.token ?? '';
  }
}

function parse<T>(frame: IMessage): T {
  return JSON.parse(frame.body) as T;
}
