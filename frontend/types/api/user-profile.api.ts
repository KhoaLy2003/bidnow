export interface UserProfileResponse {
  userId: string;
  displayName: string | null;
  avatarUrl: string | null;
  phoneNumber: string | null;
  address: string | null;
  city: string | null;
  country: string | null;
  postalCode: string | null;
  bio: string | null;
  roles: string[];
  language: string | null;
  timezone: string | null;
  currency: string | null;
  emailNotifications: boolean | null;
  pushNotifications: boolean | null;
  smsNotifications: boolean | null;
  createdAt: string;
  updatedAt: string;
}

/** `GET /api/v1/users/{id}/public-profile`: the only fields anyone may see. Contact details and roles are never sent. */
export interface PublicUserProfileResponse {
  userId: string;
  displayName: string | null;
  avatarUrl: string | null;
  bio: string | null;
  city: string | null;
  country: string | null;
  /** ISO local date-time of when the user joined. */
  memberSince: string;
}

export interface UpdateUserProfileRequest {
  displayName?: string | null;
  avatarUrl?: string | null;
  phoneNumber?: string | null;
  address?: string | null;
  city?: string | null;
  country?: string | null;
  postalCode?: string | null;
  bio?: string | null;
}
