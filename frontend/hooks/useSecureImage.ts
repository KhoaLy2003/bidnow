import { useState, useEffect } from 'react'
import { mediaService } from '@/services/media.service'

export function useSecureImage(initialUrl?: string | null) {
  const [resolvedUrl, setResolvedUrl] = useState<string | undefined>(undefined)

  useEffect(() => {
    let isMounted = true

    const resolveImage = async () => {
      if (!initialUrl) {
        if (isMounted) setResolvedUrl(undefined)
        return
      }

      if (initialUrl.includes('/api/v1/media/download?s3Key=')) {
        try {
          const urlObj = new URL(initialUrl)
          const s3Key = urlObj.searchParams.get('s3Key')
          if (!s3Key) return
          const presignedUrl = await mediaService.getDownloadUrl(s3Key)
          if (isMounted) setResolvedUrl(presignedUrl)
        } catch (error) {
          console.error("Failed to load secure image", error)
        }
      } else if (initialUrl.startsWith('uploads/')) {
        try {
          const presignedUrl = await mediaService.getDownloadUrl(initialUrl)
          if (isMounted) setResolvedUrl(presignedUrl)
        } catch (error) {
          console.error("Failed to load secure image", error)
        }
      } else if (isMounted) {
        setResolvedUrl(initialUrl)
      }
    }

    resolveImage()

    return () => { isMounted = false }
  }, [initialUrl])

  return resolvedUrl
}
