export type Role = 'CLIENT' | 'TASKER' | 'ADMIN';

export interface CurrentUser {
  id: string;
  email: string;
  fullName: string;
  roles: Role[];
}
