const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL!
const SUPABASE_BUCKET = process.env.NEXT_PUBLIC_SUPABASE_BUCKET!

export function useSecureImage(value?: string | null): string | undefined {
  if (!value) return undefined
  // Already a full URL (new records written after this change)
  if (value.startsWith('http')) return value
  // Legacy s3Key stored in DB before this change (e.g. "uploads/uuid/file.jpg")
  return `${SUPABASE_URL}/storage/v1/object/public/${SUPABASE_BUCKET}/${value}`
}
