export function resolveImageUrl(value?: string | null): string | undefined {
  if (!value) return undefined
  // Already a full URL (written by media-service at upload time, or a pre-migration URL)
  if (value.startsWith('http')) return value
  const cdnUrl = process.env.NEXT_PUBLIC_CDN_URL
  if (!cdnUrl) {
    console.error('Missing NEXT_PUBLIC_CDN_URL')
    return undefined
  }
  // Bare s3Key (e.g. "uploads/uuid/file.jpg") served through CloudFront
  return `${cdnUrl.replace(/\/$/, '')}/${value.replace(/^\//, '')}`
}
