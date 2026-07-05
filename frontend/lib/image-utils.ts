const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL
const SUPABASE_BUCKET = process.env.NEXT_PUBLIC_SUPABASE_BUCKET

export function resolveImageUrl(value?: string | null): string | undefined {
  if (!value) return undefined
  if (!SUPABASE_URL || !SUPABASE_BUCKET) {
    console.error('Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_BUCKET')
    return undefined
  }
  // Already a full URL (new records written after this change)
  if (value.startsWith('http')) return value
  // Legacy s3Key stored in DB before this change (e.g. "uploads/uuid/file.jpg")
  return `${SUPABASE_URL}/storage/v1/object/public/${SUPABASE_BUCKET}/${value}`
}
