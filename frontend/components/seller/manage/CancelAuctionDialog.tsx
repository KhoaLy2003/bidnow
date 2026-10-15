'use client'

import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatCurrency } from '@/lib/format'

/** Server limit on CancelAuctionRequest.reason. */
export const CANCEL_REASON_MAX = 500

interface CancelAuctionDialogProps {
  open:         boolean
  auctionTitle: string
  totalBids:    number
  currentBid:   number
  submitting:   boolean
  onClose():    void
  /** The reason may be blank. */
  onConfirm(reason: string): void
}

export function CancelAuctionDialog({
  open, auctionTitle, totalBids, currentBid, submitting, onClose, onConfirm,
}: CancelAuctionDialogProps) {
  const [reason, setReason] = useState('')
  const [wasOpen, setWasOpen] = useState(open)
  const hasBids = totalBids > 0

  // Start every opening with an empty reason.
  if (open !== wasOpen) {
    setWasOpen(open)
    if (!open) setReason('')
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next && !submitting) onClose() }}>
      <DialogContent className="max-w-lg gap-4" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle className="font-display text-xl font-medium">Cancel this auction?</DialogTitle>
          <DialogDescription className="text-sm leading-relaxed text-[var(--color-text-secondary)]">
            “{auctionTitle}”{' '}
            {hasBids ? (
              <>
                has <span className="font-mono">{totalBids}</span> {totalBids === 1 ? 'bid' : 'bids'} and a current bid of{' '}
                <span className="font-mono">{formatCurrency(currentBid)}</span>.{' '}
              </>
            ) : (
              'has no bids yet. '
            )}
            Cancelling ends it now with no winner. This can&apos;t be undone.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="cancel-reason">
            Reason <span className="font-normal text-muted-foreground">(optional)</span>
          </Label>
          <Textarea
            id="cancel-reason"
            rows={3}
            maxLength={CANCEL_REASON_MAX}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Why are you cancelling this auction?"
            aria-describedby="cancel-reason-count"
            className="resize-none"
          />
          <p id="cancel-reason-count" className="flex justify-between text-xs text-muted-foreground">
            <span>Kept with the auction&apos;s history</span>
            <span className="font-mono">{reason.length}/{CANCEL_REASON_MAX}</span>
          </p>
        </div>

        <DialogFooter className="gap-2 sm:justify-end">
          <Button variant="outline" disabled={submitting} onClick={onClose}>Keep auction</Button>
          <Button variant="destructive" disabled={submitting} onClick={() => onConfirm(reason)}>
            {submitting && <Loader2 className="mr-1.5 size-3.5 animate-spin" aria-hidden />}
            Cancel auction
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
