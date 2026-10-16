/** An active auction the signed-in user has bid on. Amounts are dollars; the list is soonest-ending first. */
export interface MyActiveBid {
  auctionId:       string
  title:           string
  primaryImageUrl: string | null
  currentPrice:    number
  myHighestBid:    number
  totalBids:       number
  endTime:         Date
  lastBidAt:       Date
  isWinning:       boolean
}

export type MyBidsTab = 'all' | 'winning' | 'outbid'

export type MyBidCounts = Record<MyBidsTab, number>
