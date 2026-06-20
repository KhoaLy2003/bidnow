import { ApiResponse } from "@/types/api/auth.api";
import { UpdateUserProfileRequest, UserProfileResponse } from "@/types/api/user-profile.api";
import { apiFetch } from "@/lib/apiClient";

export const userService = {
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
