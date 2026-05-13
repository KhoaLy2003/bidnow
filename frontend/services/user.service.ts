import { ApiResponse } from "@/types/auth";
import { UserProfileResponse } from "@/types/user-profile";

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
};
