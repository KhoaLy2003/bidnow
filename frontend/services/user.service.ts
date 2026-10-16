import { ApiResponse } from "@/types/api/auth.api";
import { PublicUserProfileResponse, UpdateUserProfileRequest, UserProfileResponse } from "@/types/api/user-profile.api";
import { mapPublicProfile } from "@/types/mappers/user-profile.mapper";
import type { PublicProfile } from "@/types/ui/user-profile.ui";
import { apiFetch } from "@/lib/apiClient";

export const userService = {
  /** Anyone's public profile (no sign-in needed). Resolves null when the user does not exist or the id is malformed. */
  async getPublicProfile(userId: string): Promise<PublicProfile | null> {
    const response = await apiFetch(`/api/v1/users/${encodeURIComponent(userId)}/public-profile`, { cache: 'no-store' });
    if (response.status === 404 || response.status === 400) return null;
    if (!response.ok) throw new Error(`Failed to load profile (${response.status})`);
    const body: ApiResponse<PublicUserProfileResponse> = await response.json();
    return mapPublicProfile(body.data);
  },

  async getMyProfile(): Promise<ApiResponse<UserProfileResponse>> {
    const response = await apiFetch('/api/v1/users/me');
    if (!response.ok) {
      const error = await response.json();
      throw error;
    }
    return response.json();
  },

  async updateMyProfile(
    data: UpdateUserProfileRequest
  ): Promise<ApiResponse<UserProfileResponse>> {
    const response = await apiFetch('/api/v1/users/me', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
    if (!response.ok) {
      const error = await response.json();
      throw error;
    }
    return response.json();
  },
};
