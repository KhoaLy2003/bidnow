import { Trophy, Bot } from 'lucide-react'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { UserAvatar } from '@/components/shared/UserAvatar'
import { formatCurrency, formatRelativeTime } from '@/lib/format'
import type { BidHistoryItem } from '@/types/ui/auction.ui'
import { cn } from '@/lib/utils'

interface BidHistoryProps {
  items:      BidHistoryItem[]
  onLoadMore?: () => void
  hasMore?:   boolean
  className?: string
}

export function BidHistory({ items, onLoadMore, hasMore, className }: BidHistoryProps) {
  if (items.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-muted-foreground">
        No bids yet — be the first!
      </p>
    )
  }

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <ScrollArea className="max-h-80">
        <ul className="flex flex-col">
          {items.map((item, i) => (
            <li key={item.id}>
              {i > 0 && <Separator />}
              <div
                className={cn(
                  'flex items-center gap-3 px-2 py-2.5 rounded-md',
                  item.isCurrentUser && 'bg-accent',
                )}
              >
                <UserAvatar
                  name={item.bidderName}
                  avatarUrl={item.bidderAvatarUrl}
                  size="sm"
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1">
                    <span className="text-sm font-medium truncate">
                      {item.isCurrentUser ? 'You' : item.bidderName}
                    </span>
                    {item.isAutoBid && (
                      <Bot className="size-3 text-muted-foreground shrink-0" />
                    )}
                    {item.isWinning && (
                      <Trophy className="size-3 text-[var(--color-success-text)] shrink-0" />
                    )}
                  </div>
                  <span className="text-xs text-muted-foreground">
                    {formatRelativeTime(item.placedAt)}
                  </span>
                </div>
                <span
                  className={cn(
                    'font-mono font-medium text-sm shrink-0',
                    item.isWinning && 'text-[var(--color-success-text)]',
                  )}
                >
                  {formatCurrency(item.amount)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      </ScrollArea>

      {hasMore && (
        <Button variant="link" size="sm" onClick={onLoadMore} className="self-center">
          Load more
        </Button>
      )}
    </div>
  )
}
