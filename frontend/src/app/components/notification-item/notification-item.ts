import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AppNotification } from '../../api/models';
import { Icon } from '../icon/icon';
import { notificationKind } from '../../shared/notification-kind/notification-kind';
import { timeAgo } from '../../shared/format/format';

@Component({
  selector: 'app-notification-item',
  imports: [Icon],
  templateUrl: './notification-item.html',
})
export class NotificationItem {
  @Input({ required: true }) notification!: AppNotification;
  @Input() compact = false;
  @Output() readonly opened = new EventEmitter<AppNotification>();

  readonly timeAgo = timeAgo;

  get kind() {
    return notificationKind(this.notification.type);
  }
}
