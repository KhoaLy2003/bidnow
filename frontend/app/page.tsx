import type { Metadata } from 'next'
import Link from 'next/link'
import {
  Flame,
  Star,
  ArrowRight,
  Cpu,
  Gem,
  Shirt,
  Watch,
  Music,
  Palette,
  UserPlus,
  Gavel,
  CheckCircle,
  ShieldCheck,
  Lock,
  Zap,
  Headphones,
} from 'lucide-react'
import { Header }        from '@/components/layout/Header'
import { Footer }        from '@/components/layout/Footer'
import { BottomNav }     from '@/components/layout/BottomNav'
import { AuctionBrowseGrid } from '@/components/auction/browse'
import { Button }        from '@/components/ui/button'
import { CTASection }    from '@/components/home/CTASection'
import { FAQAccordion }  from '@/components/home/FAQAccordion'
import { AuctionStatus } from '@/lib/design-tokens'
import { auctionService } from '@/services/auction.service'

export const metadata: Metadata = {
  title: 'BidNow — Bid. Win. Repeat.',
  description: 'Live auctions for rare collectibles, luxury watches, vintage instruments, and more.',
}

const STATS = [
  { value: '50K+',   label: 'Active Users' },
  { value: '$2.5M+', label: 'Total Auction Value' },
  { value: '4.8★',   label: 'User Rating' },
] as const

const CATEGORIES = [
  { label: 'Electronics',  slug: 'electronics',  Icon: Cpu,     color: 'text-[var(--color-info-default)]',    bg: 'bg-[var(--color-info-subtle)]' },
  { label: 'Collectibles', slug: 'collectibles',  Icon: Gem,     color: 'text-[var(--color-success-default)]', bg: 'bg-[var(--color-success-subtle)]' },
  { label: 'Fashion',      slug: 'fashion',       Icon: Shirt,   color: 'text-[var(--color-warning-default)]', bg: 'bg-[var(--color-warning-subtle)]' },
  { label: 'Watches',      slug: 'watches',       Icon: Watch,   color: 'text-[var(--color-brand-600)]',       bg: 'bg-[var(--color-brand-50)]' },
  { label: 'Instruments',  slug: 'instruments',   Icon: Music,   color: 'text-orange-500',                     bg: 'bg-orange-50' },
  { label: 'Art',          slug: 'art',           Icon: Palette, color: 'text-rose-500',                       bg: 'bg-rose-50' },
] as const

const HOW_IT_WORKS = [
  { step: 1, Icon: UserPlus,    title: 'Register & Verify', description: 'Create an account and confirm your identity with a one-time code.' },
  { step: 2, Icon: Gavel,       title: 'Browse & Bid',      description: 'Find items you love and place competitive real-time bids.' },
  { step: 3, Icon: CheckCircle, title: 'Pay & Receive',     description: 'Win the auction, pay securely, and get your item delivered.' },
] as const

const TRUST_CARDS = [
  { Icon: ShieldCheck, title: 'Verified Sellers',  body: 'Every seller is reviewed and approved before listing.' },
  { Icon: Lock,        title: 'Secure Payments',   body: 'Payments are encrypted and held in escrow until delivery.' },
  { Icon: Zap,         title: 'Real-time Bidding', body: 'Live price updates — no page refreshes needed.' },
  { Icon: Headphones,  title: '24/7 Support',       body: 'Our team is available around the clock to help.' },
] as const

export default async function HomePage() {
  const [{ items: featuredPicks }, { items: allAuctions }] = await Promise.all([
    auctionService.getBrowseAuctions({ sortBy: 'END_TIME_ASC', size: 8 }),
    auctionService.getBrowseAuctions({ size: 20 }),
  ])
  const hotAuctions = allAuctions.filter(
    (a) => a.status === AuctionStatus.Critical || a.status === AuctionStatus.EndingSoon,
  )

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-xl)] px-4 pb-14 md:pb-8">

        {/* Hero */}
        <section className="relative overflow-hidden rounded-2xl mt-8 mb-12 bg-gradient-to-br from-[var(--color-brand-600)] to-[var(--color-brand-900)] px-8 py-16 text-white sm:px-16">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--color-brand-400)_0%,_transparent_60%)] opacity-40" />
          <div className="relative flex flex-col gap-6 max-w-xl">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 text-sm font-medium backdrop-blur-sm">
              <span className="size-2 rounded-full bg-[var(--color-auction-active-accent)] animate-pulse" />
              Live auctions happening now
            </div>
            <h1 className="font-display font-medium text-[length:var(--font-size-5xl)] leading-none">
              Bid. Win.<br />Repeat.
            </h1>
            <p className="text-base text-white/80 max-w-sm">
              Rare collectibles, luxury watches, vintage instruments — all in one place. Real-time bidding, zero compromise.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button variant="brand" className="h-11 bg-white text-[var(--color-brand-700)] hover:bg-white/90" render={<Link href="/auctions" />} nativeButton={false}>
                Browse Auctions
                <ArrowRight className="size-4" />
              </Button>
              <Button variant="outline" className="h-11 border-white/30 bg-transparent text-white hover:bg-white/10" render={<Link href="/auctions/new" />} nativeButton={false}>
                Start Selling
              </Button>
            </div>
          </div>
        </section>

        {/* Stats */}
        <section className="grid grid-cols-3 gap-4 mb-12">
          {STATS.map(({ value, label }) => (
            <div key={label} className="rounded-xl bg-muted px-4 py-6 text-center">
              <p className="font-mono font-bold text-[length:var(--font-size-2xl)] text-[var(--color-text-primary)]">
                {value}
              </p>
              <p className="text-xs text-muted-foreground mt-1">{label}</p>
            </div>
          ))}
        </section>

        {/* Ending Soon */}
        {hotAuctions.length > 0 && (
          <section className="flex flex-col gap-4 mb-12">
            <div className="flex items-center justify-between">
              <h2 className="font-display font-medium text-[length:var(--font-size-xl)] flex items-center gap-2">
                <Flame className="size-5 text-[var(--color-danger-default)]" />
                Ending Soon
              </h2>
              <Link href="/auctions?sort=ending" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
                View all <ArrowRight className="size-3.5" />
              </Link>
            </div>
            <AuctionBrowseGrid items={hotAuctions} />
          </section>
        )}

        {/* Featured Picks */}
        <section className="flex flex-col gap-4 mb-12">
          <div className="flex items-center justify-between">
            <h2 className="font-display font-medium text-[length:var(--font-size-xl)] flex items-center gap-2">
              <Star className="size-5 text-[var(--color-warning-text)]" />
              Featured Picks
            </h2>
            <Link href="/auctions?featured=true" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
              View all <ArrowRight className="size-3.5" />
            </Link>
          </div>
          <AuctionBrowseGrid items={featuredPicks} />
        </section>

        {/* Category Browse */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-6">
            Browse by Category
          </h2>
          <div className="grid grid-cols-3 md:grid-cols-6 gap-3">
            {CATEGORIES.map(({ label, slug, Icon, color, bg }) => (
              <Link
                key={slug}
                href={`/auctions?category=${slug}`}
                className="group flex flex-col items-center gap-2 rounded-xl p-4 hover:bg-muted transition-colors duration-[var(--duration-tesla)]"
              >
                <div className={`rounded-full p-3 ${bg}`}>
                  <Icon className={`size-6 ${color}`} />
                </div>
                <span className="text-xs font-medium text-center group-hover:text-[var(--color-text-brand)] transition-colors">
                  {label}
                </span>
              </Link>
            ))}
          </div>
        </section>

        {/* How It Works */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-8">
            How It Works
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {HOW_IT_WORKS.map(({ step, Icon, title, description }) => (
              <div key={step} className="flex flex-col gap-4">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-brand-600)] text-white text-sm font-medium">
                    {step}
                  </span>
                  <Icon className="size-6 text-[var(--color-text-tertiary)]" />
                </div>
                <div>
                  <h3 className="font-medium text-sm mb-1">{title}</h3>
                  <p className="text-sm text-muted-foreground">{description}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Trust & Safety */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-6">
            Why Trust BidNow?
          </h2>
          <div className="grid grid-cols-2 gap-4">
            {TRUST_CARDS.map(({ Icon, title, body }) => (
              <div key={title} className="rounded-xl border border-[var(--color-border-default)] bg-card p-5 flex flex-col gap-3">
                <Icon className="size-5 text-[var(--color-brand-600)]" />
                <div>
                  <h3 className="font-medium text-sm mb-1">{title}</h3>
                  <p className="text-xs text-muted-foreground">{body}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* CTA — guest only, hides itself client-side */}
        <CTASection />

        {/* FAQ */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-6">
            Frequently Asked Questions
          </h2>
          <FAQAccordion />
        </section>

      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
