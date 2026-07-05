'use client'

import { useState } from 'react'
import Image, { type ImageProps } from 'next/image'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

type ImageThumbnailProps = ImageProps & {
  wrapperClassName?: string
}

export function ImageThumbnail({
  className,
  wrapperClassName,
  onLoad,
  fill,
  alt,
  ...props
}: ImageThumbnailProps) {
  const [loaded, setLoaded] = useState(false)

  return (
    <div className={cn('relative', fill && 'size-full', wrapperClassName)}>
      {!loaded && (
        <Skeleton className="absolute inset-0 size-full rounded-none" />
      )}
      <Image
        {...props}
        alt={alt}
        fill={fill}
        className={cn(
          'transition-opacity duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
          loaded ? 'opacity-100' : 'opacity-0',
          className,
        )}
        onLoad={(e) => {
          setLoaded(true)
          onLoad?.(e)
        }}
      />
    </div>
  )
}
