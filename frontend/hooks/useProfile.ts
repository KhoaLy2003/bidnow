"use client";

import { useState, useEffect, useCallback } from "react";
import { useAuthStore } from "@/store/authStore";
import { userService } from "@/services/user.service";
import { UpdateUserProfileRequest, UserProfileResponse } from "@/types/api/user-profile.api";

interface UseProfileResult {
  profile: UserProfileResponse | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
  updateProfile: (data: UpdateUserProfileRequest) => Promise<UserProfileResponse>;
}

export function useProfile(): UseProfileResult {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchProfile = useCallback(async () => {
    if (!isAuthenticated) return;

    setIsLoading(true);
    setError(null);

    try {
      const res = await userService.getMyProfile();
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
  }, [isAuthenticated]);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  const updateProfile = useCallback(
    async (data: UpdateUserProfileRequest): Promise<UserProfileResponse> => {
      if (!isAuthenticated) {
        throw new Error("Not authenticated");
      }

      setIsLoading(true);
      setError(null);

      try {
        const res = await userService.updateMyProfile(data);
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
    [isAuthenticated]
  );

  return { profile, isLoading, error, refetch: fetchProfile, updateProfile };
}
