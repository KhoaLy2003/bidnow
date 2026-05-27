export type MediaEntityType = 'USER_AVATAR' | 'AUCTION_ITEM';

export interface MediaUploadResponse {
  id: string;
  originalName: string;
  s3Key: string;
  contentType: string;
  fileSize: number;
  width: number;
  height: number;
  createdAt: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  s3Key: string;
  expiresAt: string;
}
