import { Section, SectionHeading } from './Section'

const STEPS = [
  {
    n: '01',
    title: 'Lock a deposit',
    body: "It's held when you place your first bid. Lose, and it returns to your wallet the moment the auction ends.",
  },
  {
    n: '02',
    title: 'Bid, or set a max',
    body: 'Auto-bid raises your offer one step at a time, only as far as the limit you chose.',
  },
  {
    n: '03',
    title: 'Watch the clock',
    body: 'Prices, bid counts and the countdown update live. When time is short, a late bid extends the auction for everyone.',
  },
  {
    n: '04',
    title: 'Win and pay',
    body: 'Pay within 48 hours and the lot is yours. Sellers are reviewed and approved before they list.',
  },
] as const

/** The three parts of an auction's life; widths are illustrative, not to scale. */
const TIMELINE = [
  {
    tag: 'OPEN BIDDING',
    label: 'Every bid appears for everyone, instantly',
    caption: 'Bid by hand or set a max and let auto-bid answer for you.',
    bar: 'bg-[var(--color-brand-100)] text-[var(--color-brand-700)] dark:bg-[var(--color-brand-800)] dark:text-[var(--color-brand-100)]',
    col: 'md:col-span-6',
  },
  {
    tag: 'LAST 2:00',
    label: 'Anti-snipe zone',
    caption: 'A bid here moves the end time.',
    bar: 'bg-[repeating-linear-gradient(135deg,var(--color-warning-border)_0_7px,var(--color-warning-subtle)_7px_14px)] text-[var(--color-warning-text)] justify-center',
    col: 'md:col-span-2',
  },
  {
    tag: 'THEN',
    label: '+5:00',
    caption: 'Added to the current end time. Repeats as often as bidders want, with no cap.',
    bar: 'border-[1.5px] border-dashed border-[var(--color-brand-500)] bg-[var(--color-accent)] text-[var(--color-text-brand)] justify-center font-mono',
    col: 'md:col-span-3',
  },
] as const

export function HowItWorks() {
  return (
    <Section id="how-it-works-live" tinted>
      <SectionHeading
        eyebrow="How a live auction works"
        title="Fair by design, fast by nature."
        description="Most auctions are won by whoever clicks in the last second. Here the last second doesn't exist."
      />

      <div className="grid gap-5 md:grid-cols-11 md:gap-1.5" role="img" aria-label="Timeline: open bidding, then the last two minutes, then a five minute extension each time a bid lands in that window">
        {TIMELINE.map(({ tag, label, caption, bar, col }) => (
          <div key={tag} className={col} aria-hidden="true">
            <span className="mb-2 block font-mono text-[length:var(--font-size-xs)] text-muted-foreground">{tag}</span>
            <div className={`flex h-[3.25rem] items-center rounded-lg px-4 text-sm ${bar}`}>{label}</div>
            <p className="mt-3 pr-3 text-sm leading-relaxed text-muted-foreground">{caption}</p>
          </div>
        ))}
      </div>

      <ol className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map(({ n, title, body }) => (
          <li key={n} className="rounded-xl border border-[var(--color-border-default)] bg-card p-6">
            <span className="font-mono text-[length:var(--font-size-xs)] font-medium text-[var(--color-text-brand)]">{n}</span>
            <h3 className="mt-9 font-display text-[length:var(--font-size-lg)] font-medium leading-tight">{title}</h3>
            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{body}</p>
          </li>
        ))}
      </ol>
    </Section>
  )
}
