import type { PublicUserProfileResponse } from '@/types/api/user-profile.api'
import type { PublicProfile } from '@/types/ui/user-profile.ui'

const FALLBACK_NAME = 'BidNow member'

function clean(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

export function mapPublicProfile(dto: PublicUserProfileResponse): PublicProfile {
  const location = [clean(dto.city), clean(dto.country)].filter(Boolean).join(', ')
  return {
    id:          dto.userId,
    name:        clean(dto.displayName) ?? FALLBACK_NAME,
    avatarUrl:   clean(dto.avatarUrl),
    bio:         clean(dto.bio),
    location:    location || undefined,
    memberSince: new Date(dto.memberSince),
  }
}
