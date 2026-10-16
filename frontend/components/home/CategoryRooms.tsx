import Link from 'next/link'
import { ArrowRight, Cpu, Gem, Music, Palette, Shirt, Watch, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { CategoryCount } from '@/types/ui/auction-browse.ui'
import { Section, SectionHeading } from './Section'

interface Room {
  slug:   string
  label:  string
  Icon:   LucideIcon
  /** Tile background, label colour and the big icon's colour. */
  tile:   string
  accent: string
  layout: string
}

const ROOMS: Room[] = [
  { slug: 'watches',      label: 'Watches',      Icon: Watch,   tile: 'bg-[var(--color-accent)]',        accent: 'text-[var(--color-text-brand)]',          layout: 'col-span-2 lg:row-span-2' },
  { slug: 'instruments',  label: 'Instruments',  Icon: Music,   tile: 'bg-orange-500/10',                accent: 'text-orange-600 dark:text-orange-300',    layout: 'col-span-2' },
  { slug: 'electronics',  label: 'Electronics',  Icon: Cpu,     tile: 'bg-[var(--color-info-subtle)]',    accent: 'text-[var(--color-info-text)]',           layout: '' },
  { slug: 'collectibles', label: 'Collectibles', Icon: Gem,     tile: 'bg-[var(--color-success-subtle)]', accent: 'text-[var(--color-success-text)]',        layout: '' },
  { slug: 'fashion',      label: 'Fashion',      Icon: Shirt,   tile: 'bg-[var(--color-warning-subtle)]', accent: 'text-[var(--color-warning-text)]',        layout: 'col-span-2' },
  { slug: 'art',          label: 'Art',          Icon: Palette, tile: 'bg-rose-500/10',                  accent: 'text-rose-600 dark:text-rose-300',        layout: 'col-span-2' },
]

interface CategoryRoomsProps {
  counts: CategoryCount[]
}

/** Category tiles as a bento grid; each shows its live auction count when the API provides one. */
export function CategoryRooms({ counts }: CategoryRoomsProps) {
  const bySlug = new Map(counts.map((c) => [c.slug, c.count]))

  return (
    <Section id="categories">
      <SectionHeading eyebrow="Browse by category" title="Pick a room. Auctions are already running." />

      <div className="grid auto-rows-[9.5rem] grid-cols-2 gap-3 md:gap-4 lg:auto-rows-[10.5rem] lg:grid-cols-4">
        {ROOMS.map(({ slug, label, Icon, tile, accent, layout }, index) => {
          const count = bySlug.get(slug)
          const feature = index === 0
          return (
            <Link
              key={slug}
              href={`/auctions?category=${slug}`}
              className={cn(
                'group relative flex flex-col justify-between overflow-hidden rounded-xl p-4 transition-opacity duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:opacity-90 md:p-5',
                tile,
                layout,
              )}
            >
              <span className={cn('font-mono text-[length:var(--font-size-xs)] font-medium uppercase', accent)}>
                {count === undefined ? 'Browse' : `${count} live`}
              </span>
              <span className={cn('relative z-10 flex items-center gap-2 font-display font-medium', accent, feature ? 'text-[length:var(--font-size-3xl)]' : 'text-[length:var(--font-size-xl)]')}>
                {label}
                <ArrowRight className="size-5 opacity-70 transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:translate-x-0.5" aria-hidden="true" />
              </span>
              <Icon
                className={cn('absolute -bottom-3 -right-2 opacity-25', accent, feature ? 'size-44 md:size-56' : 'size-20 md:size-24')}
                strokeWidth={1.25}
                aria-hidden="true"
              />
            </Link>
          )
        })}
      </div>
    </Section>
  )
}
