import { apiFetch } from "@/lib/apiClient";
import type { MediaUploadResponse, MediaEntityType, PresignedUrlResponse } from '@/types/api/media.api';

export const mediaService = {
  uploadFile: async (
    file: File,
    entityType?: MediaEntityType,
    entityId?: string,
  ): Promise<MediaUploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    if (entityType) formData.append("entityType", entityType);
    if (entityId) formData.append("entityId", entityId);

    const response = await apiFetch('/api/v1/media/upload', {
      method: 'POST',
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
    fileName: string,
    contentType: string,
  ): Promise<PresignedUrlResponse> => {
    const params = new URLSearchParams({ fileName, contentType });
    const response = await apiFetch(`/api/v1/media/presigned-url?${params}`, {
      method: 'GET',
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
      method: 'PUT',
      headers: { 'Content-Type': file.type },
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Failed to upload to S3: ${response.statusText}`);
    }
  },
};
