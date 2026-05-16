import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Formats a date string, number, or Date object into a readable string using native Intl API.
 * Default format: "MMM dd, yyyy" (e.g., "Oct 24, 2023")
 */
export function formatDate(date: string | number | Date, options?: Intl.DateTimeFormatOptions) {
  const d = new Date(date)
  if (isNaN(d.getTime())) return 'N/A'

  const defaultOptions: Intl.DateTimeFormatOptions = {
    month: 'short',
    day: '2-digit',
    year: 'numeric',
    ...options
  }

  return new Intl.DateTimeFormat('en-US', defaultOptions).format(d)
}
