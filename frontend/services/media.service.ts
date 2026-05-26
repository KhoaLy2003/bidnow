const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

import type { MediaUploadResponse, MediaEntityType, PresignedUrlResponse } from '@/types/api/media.api';

export const mediaService = {
  uploadFile: async (
    accessToken: string,
    file: File,
    entityType?: MediaEntityType,
    entityId?: string,
  ): Promise<MediaUploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    if (entityType) formData.append("entityType", entityType);
    if (entityId) formData.append("entityId", entityId);

    const response = await fetch(`${API_URL}/api/v1/media/upload`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      body: formData,
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },

  getPresignedUrl: async (
    accessToken: string,
    fileName: string,
    contentType: string,
  ): Promise<PresignedUrlResponse> => {
    const url = new URL(`${API_URL}/api/v1/media/presigned-url`);
    url.searchParams.append("fileName", fileName);
    url.searchParams.append("contentType", contentType);

    const response = await fetch(url.toString(), {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },

  uploadToS3: async (uploadUrl: string, file: File): Promise<void> => {
    const response = await fetch(uploadUrl, {
      method: "PUT",
      headers: {
        "Content-Type": file.type,
      },
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Failed to upload to S3: ${response.statusText}`);
    }
  },

  getDownloadUrl: async (
    accessToken: string,
    s3Key: string,
  ): Promise<string> => {
    const url = new URL(`${API_URL}/api/v1/media/download`);
    url.searchParams.append("s3Key", s3Key);

    const response = await fetch(url.toString(), {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },
};
