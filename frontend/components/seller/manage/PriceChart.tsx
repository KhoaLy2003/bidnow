'use client'

import { useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, ReferenceLine, XAxis, YAxis } from 'recharts'
import {
  ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig,
} from '@/components/ui/chart'
import { formatCurrency } from '@/lib/format'
import type { BidEntry } from '@/types/ui/auction.ui'

const DAY_MS = 86_400_000

const CHART_CONFIG = {
  amount: { label: 'Bid', color: 'var(--color-text-brand)' },
} satisfies ChartConfig

const TIME_ONLY: Intl.DateTimeFormatOptions = { hour: 'numeric', minute: '2-digit' }
const DATE_AND_TIME: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }

interface PriceChartProps {
  /** Newest first. */
  bids:          BidEntry[]
  startingPrice: number
}

/**
 * How the price climbed across the loaded bids: a step line (flat between bids) over real time, with the
 * starting price as a dashed baseline. Recharts via the shadcn chart wrapper, so it follows the theme.
 */
export function PriceChart({ bids, startingPrice }: PriceChartProps) {
  const points = useMemo(
    () => [...bids].reverse().map((b) => ({ t: b.placedAt.getTime(), amount: b.amount })),
    [bids],
  )
  const first = points[0]
  const last = points[points.length - 1]
  const hasChart = points.length >= 2

  // Times only when the bids fall within a day; add the date when they span longer.
  const tickFormat = hasChart && last.t - first.t > DAY_MS ? DATE_AND_TIME : TIME_ONLY

  return (
    <section aria-labelledby="price-chart-heading" className="rounded-xl border border-[var(--color-border-default)] p-5">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="price-chart-heading" className="text-sm font-medium">Price progression</h2>
        <span className="text-xs text-muted-foreground">Last {bids.length} bids loaded</span>
      </div>

      {hasChart ? (
        <>
          <p className="sr-only">
            The bid rose from {formatCurrency(first.amount)} to {formatCurrency(last.amount)} across {points.length} bids.
          </p>
          <ChartContainer config={CHART_CONFIG} className="mt-3 aspect-auto h-[200px] w-full">
            <AreaChart accessibilityLayer data={points} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid vertical={false} />
              <XAxis
                dataKey="t"
                type="number"
                domain={['dataMin', 'dataMax']}
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                minTickGap={48}
                tickFormatter={(t: number) => new Date(t).toLocaleString('en-US', tickFormat)}
              />
              <YAxis
                // The starting price stays in view even when every bid is far above it.
                domain={[(dataMin: number) => Math.min(dataMin, startingPrice), 'dataMax']}
                width={64}
                tickLine={false}
                axisLine={false}
                tickFormatter={(v: number) => formatCurrency(v)}
              />
              <ReferenceLine
                y={startingPrice}
                stroke="var(--color-border-strong)"
                strokeDasharray="3 4"
                label={{ value: 'Starting price', position: 'insideBottomRight', fontSize: 11, fill: 'var(--muted-foreground)' }}
              />
              <ChartTooltip
                content={
                  <ChartTooltipContent
                    hideIndicator
                    labelFormatter={(_, payload) => {
                      const t = payload?.[0]?.payload?.t
                      return typeof t === 'number' ? new Date(t).toLocaleString('en-US', DATE_AND_TIME) : ''
                    }}
                    formatter={(value) => (
                      <span className="font-mono font-medium">{formatCurrency(Number(value))}</span>
                    )}
                  />
                }
              />
              <Area
                dataKey="amount"
                type="stepAfter"
                stroke="var(--color-amount)"
                strokeWidth={2}
                fill="var(--color-amount)"
                fillOpacity={0.1}
                dot={false}
                activeDot={{ r: 4 }}
                isAnimationActive={false}
              />
            </AreaChart>
          </ChartContainer>
        </>
      ) : (
        <p className="py-8 text-center text-sm text-muted-foreground">
          The chart appears once there are two bids.
        </p>
      )}
    </section>
  )
}
