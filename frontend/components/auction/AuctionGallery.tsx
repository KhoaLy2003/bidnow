'use client'

import { useState } from 'react'
import Image from 'next/image'
import { ImageIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { AuctionImageResponse } from '@/types/api/auction.api'

interface AuctionGalleryProps {
  readonly images: AuctionImageResponse[]
  readonly title:  string
}

export function AuctionGallery({ images, title }: AuctionGalleryProps) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [dragStartX, setDragStartX] = useState<number | null>(null)

  const sorted     = [...images].sort((a, b) => a.displayOrder - b.displayOrder)
  const hasImages  = sorted.length > 0
  const activeImage = sorted[activeIndex]

  function cancelDrag() { setDragStartX(null) }

  function handlePointerDown(e: React.PointerEvent) { setDragStartX(e.clientX) }

  function handlePointerUp(e: React.PointerEvent) {
    if (dragStartX === null || !hasImages) return
    const delta = e.clientX - dragStartX
    if (delta < -40 && activeIndex < sorted.length - 1) setActiveIndex(activeIndex + 1)
    if (delta > 40  && activeIndex > 0)                 setActiveIndex(activeIndex - 1)
    setDragStartX(null)
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        className="relative aspect-[4/3] w-full overflow-hidden rounded-xl bg-muted select-none"
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerCancel={cancelDrag}
        onPointerLeave={cancelDrag}
      >
        {hasImages ? (
          <Image
            src={activeImage.imageUrl}
            alt={`${title} — image ${activeIndex + 1}`}
            fill
            className="object-cover"
            sizes="(max-width: 768px) 100vw, 600px"
            priority
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <ImageIcon className="size-12 text-muted-foreground/40" />
          </div>
        )}

        {hasImages && (
          <div className="absolute right-3 top-3 rounded px-2 py-1 text-xs font-mono text-muted-foreground bg-background/90 border">
            {activeIndex + 1} / {sorted.length}
          </div>
        )}
      </div>

      {hasImages && sorted.length > 1 && (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {sorted.map((img, i) => (
            <button
              key={img.id}
              type="button"
              onClick={() => setActiveIndex(i)}
              className={cn(
                'relative size-[72px] shrink-0 overflow-hidden rounded-lg border transition-[outline,outline-offset] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
                i === activeIndex
                  ? 'outline-2 outline-[var(--color-text-brand)] outline-offset-2'
                  : 'outline-none',
              )}
            >
              <Image
                src={img.thumbnailUrl || img.imageUrl}
                alt={`${title} — thumbnail ${i + 1}`}
                fill
                className="object-cover"
                sizes="72px"
              />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
