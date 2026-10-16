import { DEFAULT_MAX_PRICE } from '@/types/ui/browse.ui'
import type { PriceRange } from '@/types/ui/browse.ui'

/**
 * The price slider runs 0-100 on a cubic curve so the low end, where most lots sit, gets
 * fine control. Positions below SLIDER_MAX map to $0-$20,000; the top position means "no maximum".
 */
export const SLIDER_MAX = 100
export const SLIDER_PRICE_CAP = 20_000
/** Minimum gap, in slider steps, between the two handles. */
export const SLIDER_MIN_GAP = 2

export interface PricePreset {
  label: string
  range: PriceRange
}

export const PRICE_PRESETS: readonly PricePreset[] = [
  { label: 'Under $250',       range: { min: 0,    max: 250 } },
  { label: '$250 – $1,000',    range: { min: 250,  max: 1_000 } },
  { label: '$1,000 – $5,000',  range: { min: 1_000, max: 5_000 } },
  { label: '$5,000 and up',    range: { min: 5_000, max: DEFAULT_MAX_PRICE } },
]

function roundPrice(value: number): number {
  if (value < 100)    return Math.round(value / 5) * 5
  if (value < 1_000)  return Math.round(value / 10) * 10
  if (value < 10_000) return Math.round(value / 50) * 50
  return Math.round(value / 500) * 500
}

/** Slider position to dollars. The top position is "no maximum". */
export function sliderToPrice(position: number): number {
  if (position >= SLIDER_MAX) return DEFAULT_MAX_PRICE
  if (position <= 0) return 0
  return roundPrice(SLIDER_PRICE_CAP * Math.pow(position / SLIDER_MAX, 3))
}

/** Dollars to slider position; anything at or above the cap that is still a real maximum sits one step below the top. */
export function priceToSlider(price: number): number {
  if (price >= DEFAULT_MAX_PRICE) return SLIDER_MAX
  if (price <= 0) return 0
  return Math.min(SLIDER_MAX - 1, Math.round(SLIDER_MAX * Math.cbrt(price / SLIDER_PRICE_CAP)))
}

/** Slider positions for a price range. */
export function rangeToSlider(range: PriceRange): [number, number] {
  return [priceToSlider(range.min), priceToSlider(range.max)]
}

/** Prices for a pair of slider positions in either order (the component enforces SLIDER_MIN_GAP). */
export function sliderToRange(low: number, high: number): PriceRange {
  const lo = Math.max(0, Math.min(low, high))
  const hi = Math.min(SLIDER_MAX, Math.max(low, high))
  return { min: sliderToPrice(lo), max: sliderToPrice(hi) }
}

/** True when the range narrows the catalogue (a minimum, or a real maximum). */
export function isPriceFiltered(range: PriceRange): boolean {
  return range.min > 0 || range.max < DEFAULT_MAX_PRICE
}

export function isSamePriceRange(a: PriceRange, b: PriceRange): boolean {
  return a.min === b.min && a.max === b.max
}

/**
 * Reads a typed price box. Empty means "no bound" (0 / no maximum); anything that is not a
 * non-negative number yields null so the caller can ignore the keystroke.
 */
export function parsePriceInput(raw: string, fallback: number): number | null {
  const text = raw.trim()
  if (text === '') return fallback
  const value = Number(text.replace(/[$,\s]/g, ''))
  if (!Number.isFinite(value) || value < 0) return null
  return Math.min(Math.round(value * 100) / 100, DEFAULT_MAX_PRICE)
}
