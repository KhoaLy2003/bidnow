'use client'

import { useState } from 'react'
import Image from 'next/image'
import { Heart, ImageIcon, Share2 } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { resolveImageUrl } from '@/lib/image-utils'
import type { AuctionImage } from '@/types/ui/auction.ui'

interface AuctionGalleryProps {
  readonly images: AuctionImage[]
  readonly title:  string
}

interface ThumbnailProps {
  img:      AuctionImage
  isActive: boolean
  index:    number
  title:    string
  onClick:  () => void
}

const OVERLAY_BUTTON =
  'flex size-11 items-center justify-center rounded-full bg-background text-foreground shadow-md transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:bg-muted'

function Thumbnail({ img, isActive, index, title, onClick }: ThumbnailProps) {
  const src = resolveImageUrl(img.thumbnailUrl || img.imageUrl)
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`Show photo ${index + 1}`}
      aria-pressed={isActive}
      className={cn(
        'relative size-16 shrink-0 overflow-hidden rounded-xl bg-muted transition-[outline,outline-offset] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] md:size-[76px]',
        isActive
          ? 'outline-2 outline-[var(--color-text-brand)] outline-offset-2'
          : 'outline-none',
      )}
    >
      {src && (
        <Image
          src={src}
          alt={`${title} — thumbnail ${index + 1}`}
          fill
          className="object-cover"
          sizes="76px"
        />
      )}
    </button>
  )
}

export function AuctionGallery({ images, title }: AuctionGalleryProps) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [dragStartX, setDragStartX]   = useState<number | null>(null)

  const sorted      = [...images].sort((a, b) => a.displayOrder - b.displayOrder)
  const hasImages   = sorted.length > 0
  const activeImage = sorted[activeIndex]

  const resolvedActiveUrl = resolveImageUrl(activeImage?.imageUrl)

  function cancelDrag() { setDragStartX(null) }

  function handlePointerDown(e: React.PointerEvent) { setDragStartX(e.clientX) }

  function handlePointerUp(e: React.PointerEvent) {
    if (dragStartX === null || !hasImages) return
    const delta = e.clientX - dragStartX
    if (delta < -40 && activeIndex < sorted.length - 1) setActiveIndex(activeIndex + 1)
    if (delta > 40  && activeIndex > 0)                 setActiveIndex(activeIndex - 1)
    setDragStartX(null)
  }

  async function handleShare() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      toast.success('Link copied')
    } catch {
      toast.error('Could not copy the link')
    }
  }

  return (
    // Thumbnails sit in a rail on the left from md up and drop below the photo on phones.
    <div className="flex flex-col-reverse gap-3 md:flex-row md:items-start md:gap-4">
      {hasImages && sorted.length > 1 && (
        <div
          role="group"
          aria-label="Photo thumbnails"
          className="flex gap-2.5 overflow-x-auto p-1 md:w-[76px] md:shrink-0 md:flex-col md:overflow-visible md:p-0"
        >
          {sorted.map((img, i) => (
            <Thumbnail
              key={img.id}
              img={img}
              isActive={i === activeIndex}
              index={i}
              title={title}
              onClick={() => setActiveIndex(i)}
            />
          ))}
        </div>
      )}

      <div
        className="relative aspect-square w-full min-w-0 flex-1 overflow-hidden rounded-[20px] bg-muted select-none"
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerCancel={cancelDrag}
        onPointerLeave={cancelDrag}
      >
        {hasImages && resolvedActiveUrl ? (
          <Image
            src={resolvedActiveUrl}
            alt={`${title} — image ${activeIndex + 1}`}
            fill
            className="object-cover"
            sizes="(max-width: 768px) 100vw, 700px"
            quality={90}
            priority
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <ImageIcon className="size-12 text-muted-foreground/40" />
          </div>
        )}

        <div className="absolute right-4 top-4 flex flex-col gap-2.5">
          <button type="button" aria-label="Save to watchlist" className={OVERLAY_BUTTON}>
            <Heart className="size-5" aria-hidden />
          </button>
          <button type="button" aria-label="Share this auction" onClick={handleShare} className={OVERLAY_BUTTON}>
            <Share2 className="size-5" aria-hidden />
          </button>
        </div>

        {hasImages && (
          <div className="absolute bottom-4 left-4 rounded-md border bg-background/90 px-2.5 py-1 font-mono text-xs font-medium text-muted-foreground">
            {activeIndex + 1} / {sorted.length}
          </div>
        )}
      </div>
    </div>
  )
}
