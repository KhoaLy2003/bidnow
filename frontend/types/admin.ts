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

export type NotificationTemplateType = "EMAIL" | "IN_APP" | "PUSH" | "SMS";

export type NotificationTemplateLanguage = "EN" | "VI";

export type EmailDeliveryStatus = "SENT" | "FAILED" | "PENDING" | "RETRY";

export interface NotificationTemplateResponse {
  id: string;
  name: string;
  type: NotificationTemplateType;
  language: NotificationTemplateLanguage;
  subject?: string | null;
  bodyHtml?: string | null;
  bodyText: string;
  variables?: string[] | null;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface NotificationTemplateRequest {
  name: string;
  type: NotificationTemplateType;
  language: NotificationTemplateLanguage;
  subject?: string | null;
  bodyHtml?: string | null;
  bodyText: string;
  variables?: string[] | null;
  active: boolean;
}

export interface TemplateFilters {
  type?: NotificationTemplateType | "ALL";
  language?: NotificationTemplateLanguage | "ALL";
  active?: boolean | "ALL";
  search?: string;
}

export interface EmailLogFilters {
  recipientEmail?: string;
  templateName?: string;
  status?: EmailDeliveryStatus | "ALL";
  search?: string;
}

export interface EmailTestRequest {
  recipientEmail: string;
  variables?: Record<string, unknown>;
}

export interface EmailLogResponse {
  id: string;
  notificationId?: string | null;
  recipientEmail: string;
  subject: string;
  templateName: string;
  status: EmailDeliveryStatus;
  failureReason?: string | null;
  retryCount: number;
  sentAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface SendTemplateEmailRequest {
  sendToAllActive?: boolean;
  recipientEmails?: string[];
  variables?: Record<string, unknown>;
}
