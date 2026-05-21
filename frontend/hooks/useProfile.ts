"use client";

import { useState, useEffect, useCallback } from "react";
import { useAuthStore } from "@/store/authStore";
import { userService } from "@/services/user.service";
import { UserProfileResponse } from "@/types/user-profile";

interface UseProfileResult {
  profile: UserProfileResponse | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
  updateProfile: (data: import("@/types/user-profile").UpdateUserProfileRequest) => Promise<UserProfileResponse>;
}

/**
 * Fetches the authenticated user's profile from the backend.
 * Relies on the access token stored in authStore — no userId is passed explicitly.
 */
export function useProfile(): UseProfileResult {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchProfile = useCallback(async () => {
    if (!isAuthenticated || !accessToken) return;

    setIsLoading(true);
    setError(null);

    try {
      const res = await userService.getMyProfile(accessToken);
      setProfile(res.data);
    } catch (err: unknown) {
      const message =
        err && typeof err === "object" && "message" in err
          ? String((err as { message: unknown }).message)
          : "Failed to load profile";
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [accessToken, isAuthenticated]);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  const updateProfile = useCallback(
    async (
      data: import("@/types/user-profile").UpdateUserProfileRequest
    ): Promise<UserProfileResponse> => {
      if (!isAuthenticated || !accessToken) {
        throw new Error("Not authenticated");
      }

      setIsLoading(true);
      setError(null);

      try {
        const res = await userService.updateMyProfile(accessToken, data);
        setProfile(res.data);
        return res.data;
      } catch (err: unknown) {
        const message =
          err && typeof err === "object" && "message" in err
            ? String((err as { message: unknown }).message)
            : "Failed to update profile";
        setError(message);
        throw new Error(message);
      } finally {
        setIsLoading(false);
      }
    },
    [accessToken, isAuthenticated]
  );

  return { profile, isLoading, error, refetch: fetchProfile, updateProfile };
}
