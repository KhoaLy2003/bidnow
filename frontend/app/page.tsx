import type { Metadata } from 'next'
import Link from 'next/link'
import { ArrowRight } from 'lucide-react'
import { Header }        from '@/components/layout/Header'
import { Footer }        from '@/components/layout/Footer'
import { BottomNav }     from '@/components/layout/BottomNav'
import { ClosingQueue }  from '@/components/home/ClosingQueue'
import { CategoryRooms } from '@/components/home/CategoryRooms'
import { CTASection }    from '@/components/home/CTASection'
import { FAQAccordion }  from '@/components/home/FAQAccordion'
import { HeroSection }   from '@/components/home/hero/HeroSection'
import { HowItWorks }    from '@/components/home/HowItWorks'
import { LiveTape }      from '@/components/home/LiveTape'
import { Section, SectionHeading } from '@/components/home/Section'
import { Button }        from '@/components/ui/button'
import { pickHeroLot }   from '@/lib/hero-watch'
import { closingNext }   from '@/lib/home-queue'
import { ROUTES }        from '@/lib/navigation'
import { auctionService } from '@/services/auction.service'

export const metadata: Metadata = {
  title: 'BidNow — Bid. Win. Repeat.',
  description: 'Live auctions for rare collectibles, luxury watches, vintage instruments, and more.',
}

const QUEUE_SIZE = 5
const TAPE_SIZE  = 8

async function loadHome() {
  const [{ items: endingFirst }, { items: latest }, categoryCounts] = await Promise.all([
    auctionService.getBrowseAuctions({ sortBy: 'END_TIME_ASC', size: 12 }),
    auctionService.getBrowseAuctions({ size: 20 }),
    auctionService.getCategoryCounts(),
  ])

  const now = Date.now()
  const lots = [...endingFirst, ...latest]
  return {
    heroLot:        pickHeroLot(lots, now),
    queue:          closingNext(lots, now, QUEUE_SIZE),
    tape:           closingNext(lots, now, TAPE_SIZE),
    categoryCounts,
  }
}

export default async function HomePage() {
  const { heroLot, queue, tape, categoryCounts } = await loadHome()

  return (
    <>
      <Header />
      <main className="flex-1 w-full pb-24 md:pb-8">
        <HeroSection lot={heroLot} />
        <LiveTape items={tape} />

        {queue.length > 0 && (
          <Section id="closing-next">
            <SectionHeading
              eyebrow="Closing next"
              title="Who runs out of time first."
              description="Every lot ranked by time left. Prices and clocks update live, no refresh needed."
              action={
                <Button
                  variant="outline"
                  size="xl"
                  className="self-start sm:self-auto"
                  render={<Link href={`${ROUTES.browse}?sort=END_TIME_ASC`} />}
                  nativeButton={false}
                >
                  See all live auctions
                  <ArrowRight className="size-4" />
                </Button>
              }
            />
            <ClosingQueue items={queue} />
          </Section>
        )}

        <HowItWorks />
        <CategoryRooms counts={categoryCounts} />
        <CTASection />

        <Section id="faq">
          <div className="mx-auto max-w-4xl">
            <SectionHeading eyebrow="Questions" title="The short answers." />
            <FAQAccordion />
          </div>
        </Section>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
