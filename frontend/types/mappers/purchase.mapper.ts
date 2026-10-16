import { resolveImageUrl } from '@/lib/image-utils'
import type { PageResponse } from '@/types/api/common.api'
import type { PurchaseResponse } from '@/types/api/purchase.api'
import type { Purchase, PurchasePage } from '@/types/ui/purchase.ui'

function toDate(value: string | null): Date | undefined {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date
}

/** Null for a row without a payment status: it is not a purchase, so the list drops it. */
export function mapPurchaseResponse(dto: PurchaseResponse): Purchase | null {
  if (!dto.paymentStatus) return null
  return {
    auctionId:       dto.auctionId,
    title:           dto.title,
    imageUrl:        resolveImageUrl(dto.primaryImageUrl),
    finalPrice:      dto.finalPrice,
    status:          dto.paymentStatus,
    completedAt:     toDate(dto.completedAt),
    paymentDeadline: toDate(dto.paymentDeadline),
    paidAt:          toDate(dto.paidAt),
  }
}

export function mapPurchasePage(response: PageResponse<PurchaseResponse>): PurchasePage {
  const { pagination } = response
  return {
    items:      response.data.map(mapPurchaseResponse).filter((p): p is Purchase => p !== null),
    page:       pagination.page,
    total:      pagination.total,
    totalPages: pagination.totalPages,
    hasNext:    pagination.hasNext,
    hasPrev:    pagination.hasPrev,
  }
}
