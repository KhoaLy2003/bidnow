'use client'

import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import {
  Dialog, DialogContent, DialogTitle,
} from '@/components/ui/dialog'
import { Button }   from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label }    from '@/components/ui/label'

interface DeleteAuctionDialogProps {
  open:         boolean
  auctionTitle: string
  onClose():    void
  onConfirm(reason: string): void
}

export function DeleteAuctionDialog({
  open, auctionTitle, onClose, onConfirm,
}: DeleteAuctionDialogProps) {
  const [reason, setReason] = useState('')

  function handleConfirm() {
    if (!reason.trim()) return
    onConfirm(reason.trim())
    setReason('')
  }

  function handleClose() {
    setReason('')
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-md gap-0 p-0" showCloseButton={false}>
        {/* Body */}
        <div className="flex flex-col gap-4 p-5">
          <div className="flex items-center gap-2">
            <AlertTriangle className="size-5 shrink-0 text-[var(--color-danger-text)]" />
            <DialogTitle className="font-medium text-base leading-none">
              Delete this auction?
            </DialogTitle>
          </div>

          <p className="text-sm text-muted-foreground">
            You&apos;re about to delete{' '}
            <span className="font-medium text-foreground">{auctionTitle}</span>.
            {' '}This auction is in{' '}
            <code className="font-mono text-xs">DRAFT</code> and has no bids — it can be deleted now.
          </p>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="delete-reason" className="text-sm font-medium">
              Reason for deleting{' '}
              <span className="text-[var(--color-danger-text)]">*</span>
            </Label>
            <Textarea
              id="delete-reason"
              placeholder="Decided to relist with better photos next week."
              value={reason}
              onChange={e => setReason(e.target.value)}
              rows={3}
              className="resize-none text-sm"
            />
            <p className="text-xs text-muted-foreground">Helps us improve the seller flow.</p>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 border-t border-[var(--color-border-default)] px-5 py-3.5">
          <Button variant="outline" size="sm" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            size="sm"
            disabled={!reason.trim()}
            onClick={handleConfirm}
            className="bg-[var(--color-danger-default)] text-white border-[var(--color-danger-default)] hover:bg-[var(--color-danger-default)]/90 disabled:opacity-50"
          >
            Delete auction
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
