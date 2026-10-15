'use client'

import Link from 'next/link'
import { Eye, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DraftEditor } from '@/components/seller/manage/DraftEditor'
import { ManageHeader } from '@/components/seller/manage/ManageHeader'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface DraftViewProps {
  auction:       AuctionDetail
  onSaved():     void
  onPublished(): void
  onDelete():    void
}

/** A draft or scheduled auction: fully editable until it starts. */
export function DraftView({ auction, onSaved, onPublished, onDelete }: DraftViewProps) {
  return (
    <div className="flex flex-col gap-6">
      <ManageHeader auction={auction}>
        <Button variant="outline" size="xl" className="px-4" render={<Link href={`/auctions/${auction.id}`} />} nativeButton={false}>
          <Eye aria-hidden />
          Preview
        </Button>
        <Button
          variant="ghost"
          size="xl"
          className="px-4 text-[var(--color-danger-text)] hover:bg-[var(--color-danger-subtle)] hover:text-[var(--color-danger-text)]"
          onClick={onDelete}
        >
          <Trash2 aria-hidden />
          Delete
        </Button>
      </ManageHeader>

      <DraftEditor auction={auction} onSaved={onSaved} onPublished={onPublished} />
    </div>
  )
}
