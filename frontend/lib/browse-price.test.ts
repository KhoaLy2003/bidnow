import { describe, expect, it } from 'vitest'
import {
  PRICE_PRESETS,
  SLIDER_MAX,
  SLIDER_MIN_GAP,
  isSamePriceRange,
  parsePriceInput,
  priceToSlider,
  rangeToSlider,
  sliderToPrice,
  sliderToRange,
} from './browse-price'
import { DEFAULT_MAX_PRICE } from '@/types/ui/browse.ui'

describe('sliderToPrice', () => {
  it('maps the ends', () => {
    expect(sliderToPrice(0)).toBe(0)
    expect(sliderToPrice(SLIDER_MAX)).toBe(DEFAULT_MAX_PRICE)
  })

  it('is monotonic', () => {
    let prev = -1
    for (let p = 0; p <= SLIDER_MAX; p++) {
      const price = sliderToPrice(p)
      expect(price).toBeGreaterThanOrEqual(prev)
      prev = price
    }
  })

  it('gives the low end fine control', () => {
    expect(sliderToPrice(10)).toBeLessThanOrEqual(20)
    expect(sliderToPrice(50)).toBe(2500)
  })
})

describe('priceToSlider', () => {
  it('maps no-max to the top and zero to the bottom', () => {
    expect(priceToSlider(DEFAULT_MAX_PRICE)).toBe(SLIDER_MAX)
    expect(priceToSlider(0)).toBe(0)
  })

  it('keeps a real maximum off the top position', () => {
    expect(priceToSlider(50_000)).toBe(SLIDER_MAX - 1)
  })

  it('round-trips slider prices', () => {
    for (const p of [12, 20, 33, 50, 71, 90]) {
      expect(priceToSlider(sliderToPrice(p))).toBe(p)
    }
  })
})

describe('sliderToRange / rangeToSlider', () => {
  it('orders the handles', () => {
    expect(sliderToRange(60, 20)).toEqual(sliderToRange(20, 60))
  })

  it('reads a preset back to the same slider positions', () => {
    const range = PRICE_PRESETS[1].range
    expect(rangeToSlider(range)).toEqual([priceToSlider(250), priceToSlider(1000)])
  })

  it('exposes a positive minimum gap', () => {
    expect(SLIDER_MIN_GAP).toBeGreaterThan(0)
  })
})

describe('parsePriceInput', () => {
  it('treats empty as the fallback bound', () => {
    expect(parsePriceInput('', 0)).toBe(0)
    expect(parsePriceInput('  ', DEFAULT_MAX_PRICE)).toBe(DEFAULT_MAX_PRICE)
  })

  it('accepts numbers with currency decoration', () => {
    expect(parsePriceInput('1,250', 0)).toBe(1250)
    expect(parsePriceInput('$99.5', 0)).toBe(99.5)
  })

  it('rejects junk and negatives', () => {
    expect(parsePriceInput('abc', 0)).toBeNull()
    expect(parsePriceInput('-5', 0)).toBeNull()
  })

  it('clamps to the ceiling', () => {
    expect(parsePriceInput('999999999', 0)).toBe(DEFAULT_MAX_PRICE)
  })
})

describe('isSamePriceRange', () => {
  it('compares both bounds', () => {
    expect(isSamePriceRange({ min: 1, max: 2 }, { min: 1, max: 2 })).toBe(true)
    expect(isSamePriceRange({ min: 1, max: 2 }, { min: 1, max: 3 })).toBe(false)
  })
})
