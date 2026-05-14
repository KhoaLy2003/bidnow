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
