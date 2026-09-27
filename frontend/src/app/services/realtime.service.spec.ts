import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Client, IMessage, StompConfig } from '@stomp/stompjs';
import { AuthService } from '../auth/auth.service';
import { LiveMessage, RealtimeService, STOMP_CLIENT_FACTORY, socketUrl } from './realtime.service';

describe('RealtimeService', () => {
  let service: RealtimeService;
  let http: HttpTestingController;
  let authService: AuthService;
  let configs: StompConfig[];
  let handlers: Map<string, (frame: IMessage) => void>;
  let deactivated: number;

  beforeEach(() => {
    configs = [];
    handlers = new Map();
    deactivated = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: STOMP_CLIENT_FACTORY,
          useValue: (config: StompConfig) => {
            configs.push(config);
            return {
              activate: () => undefined,
              deactivate: () => {
                deactivated++;
                return Promise.resolve();
              },
              subscribe: (destination: string, callback: (frame: IMessage) => void) => {
                handlers.set(destination, callback);
                return { id: destination, unsubscribe: () => undefined };
              },
            };
          },
        },
      ],
    });
    service = TestBed.inject(RealtimeService);
    http = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  function logIn(expiresIn = 900): void {
    authService.login({ email: 'amra@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'access-token',
      expiresIn,
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles: ['CLIENT'],
    });
  }

  function frame(body: unknown): IMessage {
    return { body: JSON.stringify(body) } as IMessage;
  }

  it('builds the socket address from the page address', () => {
    expect(socketUrl({ protocol: 'http:', host: 'localhost:4200' } as Location)).toBe('ws://localhost:4200/ws');
    expect(socketUrl({ protocol: 'https:', host: 'tasknest.ba' } as Location)).toBe('wss://tasknest.ba/ws');
  });

  it('connects only while someone is logged in and closes the connection on logout', () => {
    service.start();
    expect(configs.length).toBe(0);

    logIn();
    expect(configs.length).toBe(1);

    authService.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    expect(deactivated).toBeGreaterThan(0);
    expect(service.connected()).toBe(false);
  });

  it('sends the access token when it connects', async () => {
    service.start();
    logIn();

    const stomp = { connectHeaders: {} } as Client;
    await configs[0].beforeConnect!(stomp);

    expect(stomp.connectHeaders).toEqual({ Authorization: 'Bearer access-token' });
  });

  it('renews an expiring token before it connects', async () => {
    service.start();
    logIn(10);

    const stomp = { connectHeaders: {} } as Client;
    const connecting = configs[0].beforeConnect!(stomp);
    http.expectOne('/api/auth/refresh').flush({
      token: 'fresh-token',
      expiresIn: 900,
      userId: 'u1',
      email: 'amra@test.ba',
      fullName: 'Amra Hodžić',
      roles: ['CLIENT'],
    });
    await connecting;

    expect(stomp.connectHeaders).toEqual({ Authorization: 'Bearer fresh-token' });
  });

  it('listens to the own queues once connected and passes every event on', () => {
    service.start();
    logIn();
    const received: LiveMessage[] = [];
    let reconnects = 0;
    service.messages$.subscribe((live) => received.push(live));
    service.connected$.subscribe(() => reconnects++);

    configs[0].onConnect!({} as never);
    handlers.get('/user/queue/messages')!(frame({ conversationId: 'c1', message: { id: 'm1', content: 'Hi' } }));

    expect([...handlers.keys()]).toEqual(['/user/queue/messages', '/user/queue/reads', '/user/queue/notifications']);
    expect(service.connected()).toBe(true);
    expect(reconnects).toBe(1);
    expect(received).toEqual([{ conversationId: 'c1', message: { id: 'm1', content: 'Hi' } }]);
  });
});
