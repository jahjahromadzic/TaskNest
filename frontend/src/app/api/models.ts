import { components } from './schema';

type Schemas = components['schemas'];

export type AuthResponse = Schemas['AuthResponse'];
export type LoginRequest = Schemas['LoginRequest'];
export type BecomeTaskerRequest = Schemas['BecomeTaskerRequest'];
export type RegisterRequest = Schemas['RegisterRequest'];

export type Category = Schemas['CategoryResponse'];
export type Municipality = Schemas['MunicipalityResponse'];

export type TaskSummary = Schemas['TaskSummaryResponse'];
export type TaskDetail = Schemas['TaskResponse'];
export type TaskPhoto = Schemas['TaskPhotoResponse'];
export type TaskPage = Schemas['PagedResponseTaskSummaryResponse'];
export type CreateTaskRequest = Schemas['CreateTaskRequest'];
export type TaskOffer = Schemas['TaskOfferResponse'];
export type Review = Schemas['ReviewResponse'];
export type CreateReviewRequest = Schemas['CreateReviewRequest'];
export type TaskerProfile = Schemas['TaskerProfileResponse'];
export type UpdateTaskerProfileRequest = Schemas['UpdateTaskerProfileRequest'];
export type Offer = Schemas['OfferResponse'];
export type CreateOfferRequest = Schemas['CreateOfferRequest'];
export type ReviewPage = Schemas['PagedResponseReviewResponse'];
export type ReviewedAs = 'CLIENT' | 'TASKER';
export type ClientProfile = Schemas['ClientProfileResponse'];
export type ClientHire = Schemas['ClientHireResponse'];
export type ClientHirePage = Schemas['PagedResponseClientHireResponse'];
export type AppNotification = Schemas['NotificationResponse'];
export type NotificationPage = Schemas['PagedResponseNotificationResponse'];
export type NotificationType = NonNullable<AppNotification['type']>;
export type Conversation = Schemas['ConversationResponse'];
export type ConversationPage = Schemas['PagedResponseConversationResponse'];
export type ChatMessage = Schemas['MessageResponse'];
export type ChatMessagePage = Schemas['PagedResponseMessageResponse'];
export type AdminUser = Schemas['AdminUserResponse'];
export type AdminUserPage = Schemas['PagedResponseAdminUserResponse'];
export type AdminStats = Schemas['AdminStatsResponse'];
export type AdminTask = Schemas['AdminTaskResponse'];
export type AdminTaskPage = Schemas['PagedResponseAdminTaskResponse'];
