'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** Pill styling shared by every control in the browse filter bar. */
export function filterPillClass(active: boolean, className?: string) {
  return cn(
    'flex h-10 shrink-0 items-center gap-2 rounded-full border px-3.5 text-sm font-medium',
    'transition-[background-color,border-color,color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
    'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
    active
      ? 'border-brand-600 bg-accent text-[var(--color-text-brand)]'
      : 'border-border bg-background text-foreground hover:border-[var(--color-border-strong)]',
    className,
  )
}

interface FilterToggleProps {
  pressed:         boolean
  onPressedChange: (pressed: boolean) => void
  icon?:           ReactNode
  title?:          string
  children:        ReactNode
}

/** A filter that is simply on or off. Applies on click. */
export function FilterToggle({ pressed, onPressedChange, icon, title, children }: FilterToggleProps) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      title={title}
      onClick={() => onPressedChange(!pressed)}
      className={filterPillClass(pressed)}
    >
      {icon}
      <span>{children}</span>
    </button>
  )
}
