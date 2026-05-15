export interface User {
  id: string;
  email: string;
  accountStatus: 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'BANNED';
  isEmailVerified: boolean;
  isActive: boolean;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  refreshToken: string;
}

export interface ApiResponse<T> {
  timestamp: string;
  status: number;
  message: string;
  data: T;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  accountStatus: string;
  isEmailVerified: boolean;
  isActive: boolean;
  createdAt: string;
}

export interface VerifyOtpResponse {
  userId: string;
  email: string;
  accountStatus: string;
  isEmailVerified: boolean;
  verifiedAt: string;
}

export interface ResendOtpResponse {
  email: string;
  otpExpiresAt: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  errorCode: string;
  message: string;
  path: string;
  errors?: Record<string, string>;
}
