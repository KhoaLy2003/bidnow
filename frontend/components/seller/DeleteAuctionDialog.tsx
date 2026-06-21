'use client'

import { AlertTriangle } from 'lucide-react'
import {
  Dialog, DialogContent, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface DeleteAuctionDialogProps {
  readonly open:         boolean
  readonly auctionTitle: string
  onClose():   void
  onConfirm(): void
}

export function DeleteAuctionDialog({
  open, auctionTitle, onClose, onConfirm,
}: DeleteAuctionDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onClose}>
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
          </p>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 border-t border-[var(--color-border-default)] px-5 py-3.5">
          <Button variant="outline" size="sm" onClick={onClose}>
            Cancel
          </Button>
          <Button
            size="sm"
            onClick={onConfirm}
            className="bg-[var(--color-danger-default)] text-white border-[var(--color-danger-default)] hover:bg-[var(--color-danger-default)]/90"
          >
            Delete auction
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
