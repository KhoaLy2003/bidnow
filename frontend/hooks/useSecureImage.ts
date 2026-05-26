import { useState, useEffect } from 'react'
import { mediaService } from '@/services/media.service'
import { useAuthStore } from '@/store/authStore'

export function useSecureImage(initialUrl?: string | null) {
  const [resolvedUrl, setResolvedUrl] = useState<string | undefined>(undefined)
  const { accessToken } = useAuthStore()

  useEffect(() => {
    let isMounted = true

    const resolveImage = async () => {
      if (!initialUrl) {
        if (isMounted) setResolvedUrl(undefined)
        return
      }

      // Check if it's our protected backend URL
      if (initialUrl.includes('/api/v1/media/download?s3Key=')) {
        try {
          if (!accessToken) return;
          
          const urlObj = new URL(initialUrl);
          const s3Key = urlObj.searchParams.get('s3Key');
          
          if (!s3Key) return;

          const presignedUrl = await mediaService.getDownloadUrl(accessToken, s3Key);
          if (isMounted) setResolvedUrl(presignedUrl);
        } catch (error) {
          console.error("Failed to load secure image", error);
        }
      } 
      // Also check if it's just the s3Key directly (e.g. "uploads/...")
      else if (initialUrl.startsWith('uploads/')) {
        try {
          if (!accessToken) return;
          const presignedUrl = await mediaService.getDownloadUrl(accessToken, initialUrl);
          if (isMounted) setResolvedUrl(presignedUrl);
        } catch (error) {
          console.error("Failed to load secure image", error);
        }
      }
      else {
        // It's already a standard URL (or public placeholder)
        if (isMounted) setResolvedUrl(initialUrl);
      }
    }

    resolveImage();

    return () => { isMounted = false }
  }, [initialUrl, accessToken])

  return resolvedUrl
}
