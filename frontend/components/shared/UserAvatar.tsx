'use client'


import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { cn } from '@/lib/utils'
import { useSecureImage } from '@/hooks/useSecureImage'

interface UserAvatarProps {
  name: string
  avatarUrl?: string
  isOnline?: boolean
  size?: 'xs' | 'sm' | 'default' | 'lg' | 'xl'
  className?: string
}

const FALLBACK_COLORS = [
  '#4F46E5', '#0284C7', '#16A34A',
  '#D97706', '#DC2626', '#7C3AED', '#DB2777',
]

function hashColor(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i)
    hash |= 0
  }
  return FALLBACK_COLORS[Math.abs(hash) % FALLBACK_COLORS.length]
}

function getInitials(name: string): string {
  return name
    .split(' ')
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? '')
    .join('')
}

const SIZE_CLASS: Record<NonNullable<UserAvatarProps['size']>, string> = {
  xs: 'size-5 text-[10px]',
  sm: 'size-6 text-xs',
  default: 'size-8 text-sm',
  lg: 'size-10 text-sm',
  xl: 'h-32 w-32 text-2xl',
}

export function UserAvatar({
  name, avatarUrl, isOnline, size = 'default', className,
}: UserAvatarProps) {
  const initials = getInitials(name)
  const bgColor = hashColor(name)
  const resolvedUrl = useSecureImage(avatarUrl)

  return (
    <div className={cn('relative inline-flex shrink-0', className)}>
      <Avatar className={SIZE_CLASS[size]}>
        {resolvedUrl && <AvatarImage src={resolvedUrl} alt={name} />}
        <AvatarFallback
          className="font-medium text-white"
          style={{ backgroundColor: bgColor }}
        >
          {initials}
        </AvatarFallback>
      </Avatar>
      {isOnline && (
        <span className="absolute right-0 bottom-0 size-2 rounded-full bg-green-500 border-2 border-background" />
      )}
    </div>
  )
}
