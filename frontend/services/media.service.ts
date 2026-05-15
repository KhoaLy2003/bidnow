const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

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

export const mediaService = {
  uploadFile: async (
    accessToken: string,
    file: File,
    entityType?: string,
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
