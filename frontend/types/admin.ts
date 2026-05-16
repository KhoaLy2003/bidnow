export type AdminUserRole = "USER" | "ADMIN";

export type AdminUserStatus =
  | "PENDING_VERIFICATION"
  | "ACTIVE"
  | "SUSPENDED"
  | "BANNED";

export type AdminUserSortField = "createdAt" | "lastLoginAt" | "email";

export type SortDirection = "asc" | "desc";

export interface AdminUserResponse {
  id: string;
  email: string;
  role: AdminUserRole;
  status: AdminUserStatus;
  statusReason?: string | null;
  isEmailVerified: boolean;
  lastLoginAt?: string | null;
  createdAt: string;
}

export interface AdminUserProfileResponse {
  userId: string;
  displayName?: string | null;
  avatarUrl?: string | null;
  phoneNumber?: string | null;
  address?: string | null;
  city?: string | null;
  country?: string | null;
  postalCode?: string | null;
  bio?: string | null;
  roles?: string[];
  language?: string | null;
  timezone?: string | null;
  currency?: string | null;
  emailNotifications?: boolean | null;
  pushNotifications?: boolean | null;
  smsNotifications?: boolean | null;
  createdAt?: string;
  updatedAt?: string;
}
