import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AppNotification } from '../../api/models';
import { Icon } from '../icon/icon';
import { notificationKind } from '../../shared/notification-kind/notification-kind';
import { timeAgo } from '../../shared/format/format';
import { translateNotification } from '../../i18n/server-messages';
import { TranslatePipe } from '../../i18n/translate.pipe';

@Component({
  selector: 'app-notification-item',
  imports: [Icon, TranslatePipe],
  templateUrl: './notification-item.html',
})
export class NotificationItem {
  @Input({ required: true }) notification!: AppNotification;
  @Input() compact = false;
  @Output() readonly opened = new EventEmitter<AppNotification>();

  readonly timeAgo = timeAgo;

  get content(): string {
    return translateNotification(this.notification.content);
  }

  get kind() {
    return notificationKind(this.notification.type);
  }
}
