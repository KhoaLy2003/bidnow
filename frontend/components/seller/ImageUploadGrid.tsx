'use client'

import { useRef, useState, useCallback } from 'react'
import { Upload, X, Star } from 'lucide-react'
import { cn }     from '@/lib/utils'

interface ImageFile {
  file:     File
  preview:  string
  progress: number   // 0–100; 100 = done
  error?:   string
}

interface ImageUploadGridProps {
  images:    File[]
  onChange(files: File[]): void
}

const MAX_FILES   = 10
const MAX_BYTES   = 5 * 1024 * 1024   // 5 MB
const ACCEPT      = ['image/jpeg', 'image/png']

export function ImageUploadGrid({ images, onChange }: ImageUploadGridProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [items, setItems] = useState<ImageFile[]>(() =>
    images.map(f => ({ file: f, preview: URL.createObjectURL(f), progress: 100 }))
  )
  const [dragging, setDragging] = useState(false)

  function addFiles(files: FileList | File[]) {
    const arr = Array.from(files)
    const next: ImageFile[] = []

    for (const f of arr) {
      if (items.length + next.length >= MAX_FILES) break
      const error = !ACCEPT.includes(f.type)
        ? 'Only JPEG or PNG files are accepted.'
        : f.size > MAX_BYTES
        ? `File too large. Max 5 MB (this file is ${(f.size / 1024 / 1024).toFixed(1)} MB).`
        : undefined
      next.push({ file: f, preview: error ? '' : URL.createObjectURL(f), progress: error ? 100 : 0, error })
    }

    const updated = [...items, ...next]
    setItems(updated)

    // Simulate upload progress for valid files
    next.filter(i => !i.error).forEach(item => {
      let p = 0
      const tick = setInterval(() => {
        p += Math.random() * 30 + 10
        if (p >= 100) { p = 100; clearInterval(tick) }
        setItems(prev => prev.map(i => i.file === item.file ? { ...i, progress: Math.min(100, p) } : i))
      }, 120)
    })

    onChange(updated.filter(i => !i.error).map(i => i.file))
  }

  function remove(idx: number) {
    const updated = items.filter((_, i) => i !== idx)
    setItems(updated)
    onChange(updated.filter(i => !i.error).map(i => i.file))
  }

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    addFiles(e.dataTransfer.files)
  }, [items])   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="flex flex-col gap-3">
      {/* Drop zone */}
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
        onChange={e => e.target.files && addFiles(e.target.files)}
      />

      {/* Preview grid */}
      {items.length > 0 && (
        <div className="grid grid-cols-5 gap-2">
          {items.map((item, idx) => (
            <div
              key={idx}
              className={cn(
                'relative aspect-[4/3] overflow-hidden rounded-md border',
                item.error
                  ? 'border-[var(--color-danger-border)] bg-[var(--color-danger-subtle)]'
                  : 'border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]',
              )}
            >
              {/* Image or error */}
              {item.error ? (
                <div className="flex h-full flex-col gap-1 p-2">
                  <p className="text-[10px] font-medium text-[var(--color-danger-text)]">⚠ {item.error}</p>
                  <p className="text-[10px] text-[var(--color-danger-text)] truncate">{item.file.name}</p>
                </div>
              ) : (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={item.preview}
                  alt={item.file.name}
                  className="size-full object-cover"
                />
              )}

              {/* Primary badge */}
              {idx === 0 && !item.error && (
                <div className="absolute top-1 left-1 flex items-center gap-0.5 rounded-sm bg-foreground px-1 py-0.5 text-[8px] font-medium text-background">
                  <Star className="size-2" />
                  PRIMARY
                </div>
              )}

              {/* Upload progress bar */}
              {!item.error && item.progress < 100 && (
                <div className="absolute inset-x-1 bottom-1 h-1 overflow-hidden rounded-full bg-white/60">
                  <div
                    className="h-full bg-primary transition-[width] duration-100"
                    style={{ width: `${item.progress}%` }}
                  />
                </div>
              )}

              {/* Remove button */}
              <button
                type="button"
                onClick={() => remove(idx)}
                className="absolute top-1 right-1 flex size-5 items-center justify-center rounded-full bg-foreground/80 text-background hover:bg-foreground transition-colors duration-[var(--duration-tesla)]"
              >
                <X className="size-3" />
              </button>
            </div>
          ))}

          {/* Add more slot */}
          {items.length < MAX_FILES && (
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
        {items.filter(i => !i.error).length} / {MAX_FILES} images · drag to reorder · first image is the primary thumbnail
      </p>
    </div>
  )
}
