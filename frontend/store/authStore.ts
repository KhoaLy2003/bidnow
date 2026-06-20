import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { User, LoginResponse } from '@/types/api/auth.api';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  accessTokenExpiresAt: number | null;
  setAuth: (response: LoginResponse) => void;
  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      accessTokenExpiresAt: null,
      setAuth: (response: LoginResponse) =>
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          isAuthenticated: true,
          accessTokenExpiresAt: Date.now() + response.expiresIn * 1000,
          user: {
            id: response.userId,
            email: response.email,
            role: response.role,
            // These will be updated from profile if needed
            accountStatus: 'ACTIVE',
            isEmailVerified: true,
            isActive: true,
          },
        }),
      setTokens: (accessToken: string, refreshToken: string, expiresIn: number) =>
        set({ accessToken, refreshToken, accessTokenExpiresAt: Date.now() + expiresIn * 1000 }),
      logout: () =>
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
          accessTokenExpiresAt: null,
        }),
    }),
    {
      name: 'auth-storage',
    }
  )
);
