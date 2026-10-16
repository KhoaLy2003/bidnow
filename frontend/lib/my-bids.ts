import type { NotificationType } from '@/types/ui/notification.ui'
import type { MyActiveBid, MyBidCounts, MyBidsTab } from '@/types/ui/bid.ui'

/** How often the My Bids list refetches while the page is visible. */
export const MY_BIDS_POLL_MS = 30_000

export function filterByTab(bids: MyActiveBid[], tab: MyBidsTab): MyActiveBid[] {
  if (tab === 'winning') return bids.filter((b) => b.isWinning)
  if (tab === 'outbid') return bids.filter((b) => !b.isWinning)
  return bids
}

export function countByTab(bids: MyActiveBid[]): MyBidCounts {
  const winning = bids.filter((b) => b.isWinning).length
  return { all: bids.length, winning, outbid: bids.length - winning }
}

/** Notifications that mean one of the user's bids changed state, so the list should refetch. */
export function isBidStatusPush(type: NotificationType): boolean {
  return type === 'outbid' || type === 'won' || type === 'lost'
}
