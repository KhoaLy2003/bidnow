import { ApiResponse } from "@/types/api/auth.api";
import { UserProfileResponse } from "@/types/api/user-profile.api";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const userService = {
  /**
   * Fetches the profile of the currently authenticated user.
   * The backend resolves the identity from the X-User-Id header injected
   * by the API Gateway — no userId is passed in the URL.
   */
  async getMyProfile(accessToken: string): Promise<ApiResponse<UserProfileResponse>> {
    const response = await fetch(`${API_URL}/api/v1/users/me`, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    return response.json();
  },

  /**
   * Updates the profile of the currently authenticated user.
   */
  async updateMyProfile(
    accessToken: string,
    data: import("@/types/user-profile").UpdateUserProfileRequest
  ): Promise<ApiResponse<UserProfileResponse>> {
    const response = await fetch(`${API_URL}/api/v1/users/me`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    return response.json();
  },
};
