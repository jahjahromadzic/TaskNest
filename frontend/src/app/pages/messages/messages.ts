import { Component, DestroyRef, ElementRef, Injector, OnInit, ViewChild, afterNextRender, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, filter, fromEvent, map, of, switchMap } from 'rxjs';
import {
  Archive,
  ArrowLeft,
  Check,
  CheckCheck,
  CircleAlert,
  ExternalLink,
  Handshake,
  LoaderCircle,
  MessageSquare,
  MessagesSquare,
  RotateCcw,
  Search,
  SendHorizontal,
  Tag,
} from 'lucide';
import { ChatMessagePage, Conversation } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { Icon } from '../../components/icon/icon';
import { StatusBadge } from '../../components/status-badge/status-badge';
import { ConversationService } from '../../services/conversation.service';
import { OfferService } from '../../services/offer.service';
import { LiveMessage, LiveRead, RealtimeService } from '../../services/realtime.service';
import { readApiError } from '../../shared/api-error';
import {
  PendingMessage,
  avatarTone,
  buildThread,
  clockTime,
  initials,
  localTimestamp,
  mergeMessages,
  preview,
} from '../../shared/chat/chat';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { formatBudget, timeAgo } from '../../shared/format/format';
import { OFFER_STATUS, TaskStatus } from '../../shared/task-status/task-status';
import { ToastService } from '../../shared/toast/toast.service';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey, t } from '../../i18n/translate';

export const MESSAGES_PER_PAGE = 30;
export const MAX_MESSAGE_LENGTH = 5000;

type ThreadLoad = { id: string; page: ChatMessagePage | null };

const OFFER_HINTS: Record<'CLIENT' | 'TASKER', Record<'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN', TranslationKey>> = {
  CLIENT: {
    PENDING: 'messages.hintClientPending',
    ACCEPTED: 'messages.hintClientAccepted',
    REJECTED: 'messages.hintClientRejected',
    WITHDRAWN: 'messages.hintClientWithdrawn',
  },
  TASKER: {
    PENDING: 'messages.hintTaskerPending',
    ACCEPTED: 'messages.hintTaskerAccepted',
    REJECTED: 'messages.hintTaskerRejected',
    WITHDRAWN: 'messages.hintTaskerWithdrawn',
  },
};

const SILENT_NOTIFICATIONS = new Set(['NEW_MESSAGE', 'NEW_TASK_IN_AREA', 'REVIEW_RECEIVED']);

@Component({
  selector: 'app-messages',
  imports: [Icon, RouterLink, StatusBadge, TranslatePipe],
  templateUrl: './messages.html',
})
export class Messages implements OnInit {
  protected readonly icons = {
    Archive,
    ArrowLeft,
    Check,
    CheckCheck,
    CircleAlert,
    ExternalLink,
    Handshake,
    LoaderCircle,
    MessageSquare,
    MessagesSquare,
    RotateCcw,
    Search,
    SendHorizontal,
    Tag,
  };

  readonly maxLength = MAX_MESSAGE_LENGTH;
  readonly skeletons = [1, 2, 3, 4];
  readonly formatBudget = formatBudget;
  readonly timeAgo = timeAgo;
  readonly clockTime = clockTime;
  readonly avatarTone = avatarTone;
  readonly initials = initials;

  readonly conversations = signal<Conversation[] | null>(null);
  readonly listFailed = signal(false);
  readonly search = signal('');
  readonly selectedId = signal<string | null>(null);
  readonly messages = signal<PendingMessage[] | null>(null);
  readonly threadFailed = signal(false);
  readonly totalMessages = signal(0);
  readonly loadingOlder = signal(false);
  readonly draft = signal('');
  readonly accepting = signal(false);

  readonly myId = computed(() => this.authService.currentUser?.id);
  readonly selected = computed(() => this.conversations()?.find((item) => item.id === this.selectedId()) ?? null);
  readonly archived = computed(() => this.selected()?.status === 'ARCHIVED');
  readonly canAccept = computed(() => {
    const selected = this.selected();
    return selected?.viewerRole === 'CLIENT' && selected.offerStatus === 'PENDING' && selected.taskStatus === 'PUBLISHED';
  });
  readonly offerHint = computed<TranslationKey | null>(() => {
    const selected = this.selected();
    return selected?.viewerRole && selected.offerStatus ? OFFER_HINTS[selected.viewerRole][selected.offerStatus] : null;
  });
  readonly offerBadge = computed<{ tone: string; label: TranslationKey } | null>(() => {
    const status = this.selected()?.offerStatus;
    return status ? { tone: OFFER_STATUS[status].badge, label: `messages.offerState.${status}` } : null;
  });
  readonly thread = computed(() => buildThread(this.messages() ?? [], this.myId()));
  readonly hasOlder = computed(() => (this.messages() ?? []).filter((message) => !message.state).length < this.totalMessages());
  readonly missing = computed(() => this.selectedId() !== null && this.conversations() !== null && this.selected() === null);
  readonly filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    const all = this.conversations() ?? [];
    return query
      ? all.filter((item) => `${item.otherPartyName} ${item.taskTitle}`.toLowerCase().includes(query))
      : all;
  });

  @ViewChild('scroller') private scroller?: ElementRef<HTMLElement>;
  @ViewChild('composer') private composer?: ElementRef<HTMLTextAreaElement>;

  private readonly open$ = new Subject<string>();
  private resolved: Conversation | null = null;
  private page = 0;
  private localSequence = 0;

  constructor(
    private conversationService: ConversationService,
    private offerService: OfferService,
    private confirmService: ConfirmService,
    protected realtime: RealtimeService,
    private authService: AuthService,
    private toastService: ToastService,
    private route: ActivatedRoute,
    private router: Router,
    private injector: Injector,
    private destroyRef: DestroyRef,
  ) {
    this.open$
      .pipe(
        switchMap((id) =>
          this.conversationService.messages(id, 0, MESSAGES_PER_PAGE).pipe(
            map((page): ThreadLoad => ({ id, page })),
            catchError(() => of<ThreadLoad>({ id, page: null })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ id, page }) => this.showThread(id, page));

    this.realtime.messages$.pipe(takeUntilDestroyed()).subscribe((live) => this.onLiveMessage(live));
    this.realtime.reads$.pipe(takeUntilDestroyed()).subscribe((read) => this.onLiveRead(read));
    this.realtime.notifications$
      .pipe(
        filter((notification) => !SILENT_NOTIFICATIONS.has(notification.type ?? '')),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.loadConversations());
    fromEvent(document, 'visibilitychange')
      .pipe(
        filter(() => document.visibilityState === 'visible'),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        const selected = this.selected();
        if (selected?.id && (selected.unreadCount ?? 0) > 0) {
          this.markRead(selected.id);
        }
      });
  }

  ngOnInit(): void {
    this.loadConversations();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const offerId = params.get('offer');
      if (offerId) {
        this.openOffer(offerId);
        return;
      }
      const id = params.get('conversation');
      this.selectedId.set(id);
      this.draft.set('');
      if (id) {
        this.messages.set(null);
        this.threadFailed.set(false);
        this.open$.next(id);
      }
    });
  }

  loadConversations(): void {
    this.listFailed.set(false);
    this.conversationService.list().subscribe({
      next: (page) => this.conversations.set(this.withResolved(page.content ?? [])),
      error: () => this.listFailed.set(true),
    });
  }

  async acceptOffer(conversation: Conversation): Promise<void> {
    if (this.accepting() || !conversation.offerId) {
      return;
    }
    const name = conversation.otherPartyName ?? '';
    const confirmed = await this.confirmService.ask({
      title: t('offers.hireTitle', { name, price: formatBudget(conversation.offerPrice) }),
      message: t('messages.acceptMessage', { name }),
      confirmLabel: t('offers.hireConfirm'),
    });
    if (!confirmed) {
      return;
    }
    this.accepting.set(true);
    this.offerService.accept(conversation.offerId).subscribe({
      next: () => {
        this.accepting.set(false);
        this.toastService.success(t('offers.hiredToast', { name }));
        this.loadConversations();
      },
      error: (error) => {
        this.accepting.set(false);
        this.toastService.error(readApiError(error).message);
        this.loadConversations();
      },
    });
  }

  retryThread(): void {
    const id = this.selectedId();
    if (id) {
      this.threadFailed.set(false);
      this.open$.next(id);
    }
  }

  isMine(conversation: Conversation): boolean {
    return conversation.lastMessageSenderId === this.myId();
  }

  previewOf(conversation: Conversation): string {
    return preview(conversation.lastMessage, this.isMine(conversation));
  }

  bubbleClass(mine: boolean, state: PendingMessage['state']): string {
    if (!mine) {
      return 'bg-surface-card border border-surface-border rounded-2xl rounded-bl-md';
    }
    const tone = state === 'failed' ? 'bg-red-600' : 'bg-brand';
    return `${tone} text-white rounded-2xl rounded-br-md${state === 'sending' ? ' opacity-60' : ''}`;
  }

  taskStatus(conversation: Conversation): TaskStatus | undefined {
    return conversation.taskStatus as TaskStatus | undefined;
  }

  loadOlder(): void {
    const id = this.selectedId();
    if (!id || this.loadingOlder() || !this.hasOlder()) {
      return;
    }
    this.loadingOlder.set(true);
    const element = this.scroller?.nativeElement;
    const fromBottom = element ? element.scrollHeight - element.scrollTop : 0;
    this.conversationService.messages(id, this.page + 1, MESSAGES_PER_PAGE).subscribe({
      next: (older) => {
        if (this.selectedId() !== id) {
          return;
        }
        this.page++;
        this.messages.update((current) => mergeMessages(current ?? [], older.content ?? []));
        this.totalMessages.set(older.totalElements ?? this.totalMessages());
        this.loadingOlder.set(false);
        this.afterRender(() => {
          if (element) {
            element.scrollTop = element.scrollHeight - fromBottom;
          }
        });
      },
      error: () => {
        this.loadingOlder.set(false);
        this.toastService.error(t('messages.olderFailed'));
      },
    });
  }

  onDraftInput(value: string): void {
    this.draft.set(value.slice(0, MAX_MESSAGE_LENGTH));
    this.resizeComposer();
  }

  onComposerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    const id = this.selectedId();
    const content = this.draft().trim();
    if (!id || !content || this.archived()) {
      return;
    }
    const pending: PendingMessage = {
      id: `local-${++this.localSequence}`,
      senderId: this.myId(),
      content,
      createdAt: localTimestamp(),
      state: 'sending',
    };
    this.messages.update((current) => [...(current ?? []), pending]);
    this.draft.set('');
    if (this.composer) {
      this.composer.nativeElement.value = '';
    }
    this.resizeComposer();
    this.scrollToBottom();
    this.deliver(id, pending);
  }

  retry(message: PendingMessage): void {
    const id = this.selectedId();
    if (!id || this.archived()) {
      return;
    }
    this.replace(message.id, { ...message, state: 'sending' });
    this.deliver(id, message);
  }

  private deliver(conversationId: string, pending: PendingMessage): void {
    this.conversationService.send(conversationId, pending.content ?? '').subscribe({
      next: (saved) => {
        if (this.selectedId() === conversationId) {
          const pushedFirst = (this.messages() ?? []).some((item) => item.id === saved.id);
          this.replace(pending.id, saved);
          if (!pushedFirst) {
            this.totalMessages.update((total) => total + 1);
          }
        }
        this.bump(conversationId, {
          lastMessage: saved.content,
          lastMessageSenderId: saved.senderId,
          lastMessageAt: saved.createdAt,
        });
      },
      error: (error) => {
        if (this.selectedId() === conversationId) {
          this.replace(pending.id, { ...pending, state: 'failed' });
        }
        this.toastService.error(readApiError(error).message);
      },
    });
  }

  private onLiveMessage({ conversationId, message }: LiveMessage): void {
    const mine = message.senderId === this.myId();
    const watching = conversationId === this.selectedId() && document.visibilityState === 'visible';

    if (conversationId === this.selectedId() && this.messages() !== null) {
      const known = (this.messages() ?? []).some((item) => item.id === message.id);
      if (!known) {
        this.messages.update((current) => [...(current ?? []), message]);
        this.totalMessages.update((total) => total + 1);
        this.scrollToBottom();
      }
      if (!mine && watching) {
        this.markRead(conversationId);
      }
    }

    const current = this.conversations()?.find((item) => item.id === conversationId);
    if (!current) {
      if (this.conversations() !== null) {
        this.loadConversations();
      }
      return;
    }
    const unread = mine || watching ? (current.unreadCount ?? 0) : (current.unreadCount ?? 0) + 1;
    this.bump(conversationId, {
      lastMessage: message.content,
      lastMessageSenderId: message.senderId,
      lastMessageAt: message.createdAt,
      unreadCount: unread,
    });
  }

  private onLiveRead({ conversationId, readAt }: LiveRead): void {
    if (conversationId !== this.selectedId()) {
      return;
    }
    this.messages.update((current) =>
      (current ?? []).map((item) =>
        item.senderId === this.myId() && !item.readAt && !item.state ? { ...item, readAt } : item,
      ),
    );
  }

  private openOffer(offerId: string): void {
    this.conversationService.forOffer(offerId).subscribe({
      next: (conversation) => {
        this.resolved = conversation;
        this.conversations.update((list) => (list ? this.withResolved(list) : list));
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { conversation: conversation.id },
          replaceUrl: true,
        });
      },
      error: () => {
        this.toastService.error(t('messages.openFailed'));
        this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      },
    });
  }

  private showThread(id: string, page: ChatMessagePage | null): void {
    if (this.selectedId() !== id) {
      return;
    }
    if (!page) {
      this.threadFailed.set(true);
      return;
    }
    this.page = 0;
    this.messages.set(page.content ?? []);
    this.totalMessages.set(page.totalElements ?? 0);
    this.scrollToBottom();
    this.markRead(id);
  }

  private markRead(id: string): void {
    const conversation = this.selected() ?? { id, unreadCount: 0 };
    this.conversationService.markRead(conversation).subscribe({ error: () => undefined });
    this.bump(id, { unreadCount: 0 }, false);
  }

  private bump(id: string, changes: Partial<Conversation>, toTop = true): void {
    this.conversations.update((list) => {
      if (!list) {
        return list;
      }
      const current = list.find((item) => item.id === id);
      if (!current) {
        return list;
      }
      const updated = { ...current, ...changes };
      const others = list.filter((item) => item.id !== id);
      return toTop ? [updated, ...others] : list.map((item) => (item.id === id ? updated : item));
    });
  }

  private replace(id: string | undefined, message: PendingMessage): void {
    this.messages.update((current) => {
      const list = current ?? [];
      if (message.id !== id && list.some((item) => item.id === message.id)) {
        return list.filter((item) => item.id !== id);
      }
      return list.map((item) => (item.id === id ? message : item));
    });
  }

  private withResolved(list: Conversation[]): Conversation[] {
    const resolved = this.resolved;
    if (!resolved || list.some((item) => item.id === resolved.id)) {
      return list;
    }
    return [resolved, ...list];
  }

  private scrollToBottom(): void {
    this.afterRender(() => {
      const element = this.scroller?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    });
  }

  private resizeComposer(): void {
    this.afterRender(() => {
      const element = this.composer?.nativeElement;
      if (element) {
        element.style.height = 'auto';
        element.style.height = `${Math.min(element.scrollHeight, 128)}px`;
      }
    });
  }

  private afterRender(callback: () => void): void {
    afterNextRender(callback, { injector: this.injector });
  }
}
