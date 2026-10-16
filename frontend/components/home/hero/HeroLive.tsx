'use client'

import { AnimatePresence, motion } from 'motion/react'
import { formatCurrency } from '@/lib/format'
import { useLiveLot } from '@/hooks/useLiveLot'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { HeroLotCard } from './HeroLotCard'
import { WatchStage } from './WatchStage'

interface HeroLiveProps {
  /** The soonest-ending running auction, or null when nothing is running. */
  lot: AuctionBrowseItem | null
}

/** Right half of the hero: the 3D watch counting down the lot, with the live lot card under it. */
export function HeroLive({ lot }: HeroLiveProps) {
  const live = useLiveLot(lot)

  return (
    <div className="relative mx-auto w-full max-w-[34rem]">
      <WatchStage
        endsAt={live?.state.endsAt ?? null}
        pulseKey={live?.bidCount ?? 0}
        extendKey={live?.extendCount ?? 0}
      />

      <div className="pointer-events-none absolute inset-x-0 top-[10%] flex flex-col items-end gap-2" aria-live="polite">
        <AnimatePresence>
          {live?.lastBid && (
            <motion.div
              key={live.lastBid.id}
              initial={{ opacity: 0, y: 14, scale: 0.96 }}
              animate={{ opacity: [0, 1, 1, 0], y: [14, 0, 0, -10], scale: 1 }}
              transition={{ duration: 4, times: [0, 0.08, 0.85, 1], ease: 'easeOut' }}
              className="rounded-full bg-[var(--color-warning-default)] px-3.5 py-2 font-mono text-sm font-medium text-[var(--color-brand-900)]"
            >
              {live.lastBid.bidderName} bid {formatCurrency(live.lastBid.amount)}
            </motion.div>
          )}
          {live && live.extendCount > 0 && (
            <motion.div
              key={`extend-${live.extendCount}`}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: [0, 1, 1, 0], y: [14, 0, 0, -10] }}
              transition={{ duration: 4, times: [0, 0.08, 0.85, 1], ease: 'easeOut' }}
              className="rounded-full border border-white/25 bg-[var(--color-brand-800)] px-3.5 py-2 font-mono text-sm font-medium"
            >
              +5:00 added to the clock
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {lot && live && (
        <div className="relative -mt-16 sm:-mt-24">
          <HeroLotCard lot={lot} live={live} />
        </div>
      )}
    </div>
  )
}
