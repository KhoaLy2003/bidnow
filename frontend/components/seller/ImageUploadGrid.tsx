'use client'

import { useRef, useEffect, useState } from 'react'
import { Upload, X, Star } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { ManagedImage } from '@/types/ui/seller.ui'

interface ImageUploadGridProps {
  images:   ManagedImage[]
  onChange: (images: ManagedImage[]) => void
}

const MAX_FILES = 10
const MAX_BYTES = 5 * 1024 * 1024
const ACCEPT    = ['image/jpeg', 'image/png']

export function ImageUploadGrid({ images, onChange }: ImageUploadGridProps) {
  const inputRef   = useRef<HTMLInputElement>(null)
  const createdRef = useRef<string[]>([])
  const [dragging, setDragging] = useState(false)

  useEffect(() => {
    return () => { createdRef.current.forEach(url => URL.revokeObjectURL(url)) }
  }, [])

  function handleFiles(files: FileList | File[]) {
    const remaining = MAX_FILES - images.length
    const next: ManagedImage[] = Array.from(files)
      .slice(0, remaining)
      .filter(f => ACCEPT.includes(f.type) && f.size <= MAX_BYTES)
      .map(file => {
        const preview = URL.createObjectURL(file)
        createdRef.current.push(preview)
        return { kind: 'new' as const, file, preview }
      })
    onChange([...images, ...next])
  }

  function remove(index: number) {
    const img = images[index]
    if (img.kind === 'new') {
      URL.revokeObjectURL(img.preview)
      createdRef.current = createdRef.current.filter(u => u !== img.preview)
    }
    onChange(images.filter((_, i) => i !== index))
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    handleFiles(e.dataTransfer.files)
  }

  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        onDragOver={e => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex flex-col items-center justify-center gap-2 rounded-md border-[1.5px] border-dashed p-9 text-center transition-colors duration-[var(--duration-tesla)]',
          dragging
            ? 'border-primary bg-[var(--color-bg-elevated)]'
            : 'border-border bg-[var(--color-bg-elevated)] hover:border-[var(--color-border-strong)]',
        )}
      >
        <Upload className="size-7 text-muted-foreground" />
        <p className="font-medium text-sm">Drop images here</p>
        <p className="text-xs text-muted-foreground">
          or <span className="underline underline-offset-2">click to browse</span> · JPEG/PNG · 5 MB max · up to {MAX_FILES}
        </p>
      </button>

      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ACCEPT.join(',')}
        className="hidden"
        onChange={e => e.target.files && handleFiles(e.target.files)}
      />

      {images.length > 0 && (
        <div className="grid grid-cols-5 gap-2">
          {images.map((img, i) => {
            const src = img.kind === 'existing' ? img.url : img.preview
            return (
              <div
                key={img.kind === 'existing' ? img.id : img.preview}
                className="relative aspect-[4/3] overflow-hidden rounded-md border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]"
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={src} alt="" className="size-full object-cover" />
                {i === 0 && (
                  <div className="absolute top-1 left-1 flex items-center gap-0.5 rounded-sm bg-foreground px-1 py-0.5 text-[8px] font-medium text-background">
                    <Star className="size-2" />
                    PRIMARY
                  </div>
                )}
                <button
                  type="button"
                  onClick={() => remove(i)}
                  className={cn(
                    'absolute top-1 right-1 flex size-5 items-center justify-center rounded-full',
                    'bg-foreground/80 text-background hover:bg-foreground',
                    'transition-colors duration-[var(--duration-tesla)]',
                  )}
                >
                  <X className="size-3" />
                </button>
              </div>
            )
          })}

          {images.length < MAX_FILES && (
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              className="aspect-[4/3] flex flex-col items-center justify-center gap-1 rounded-md border border-dashed border-border text-muted-foreground hover:border-[var(--color-border-strong)] transition-colors duration-[var(--duration-tesla)]"
            >
              <span className="text-lg leading-none">+</span>
              <span className="text-[10px]">Add more</span>
            </button>
          )}
        </div>
      )}

      <p className="text-xs text-muted-foreground">
        {images.length} / {MAX_FILES} images · first image is the primary thumbnail
      </p>
    </div>
  )
}
