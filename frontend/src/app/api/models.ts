import { components } from './schema';

type Schemas = components['schemas'];

export type AuthResponse = Schemas['AuthResponse'];
export type LoginRequest = Schemas['LoginRequest'];
export type RegisterRequest = Schemas['RegisterRequest'];

export type Category = Schemas['CategoryResponse'];
export type Municipality = Schemas['MunicipalityResponse'];

export type TaskSummary = Schemas['TaskSummaryResponse'];
export type TaskDetail = Schemas['TaskResponse'];
export type TaskPage = Schemas['PagedResponseTaskSummaryResponse'];
export type CreateTaskRequest = Schemas['CreateTaskRequest'];
export type TaskOffer = Schemas['TaskOfferResponse'];
export type Review = Schemas['ReviewResponse'];
export type CreateReviewRequest = Schemas['CreateReviewRequest'];
export type TaskerProfile = Schemas['TaskerProfileResponse'];
export type UpdateTaskerProfileRequest = Schemas['UpdateTaskerProfileRequest'];
export type Offer = Schemas['OfferResponse'];
export type CreateOfferRequest = Schemas['CreateOfferRequest'];
