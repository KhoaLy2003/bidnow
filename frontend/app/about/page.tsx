import type { Metadata } from 'next'
import Link from 'next/link'
import { ArrowRight, ArrowUpRight } from 'lucide-react'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { Section, SectionHeading } from '@/components/home/Section'
import { ServiceMapDiagram } from '@/components/about/ServiceMapDiagram'
import { AwsArchitectureDiagram } from '@/components/about/AwsArchitectureDiagram'
import { Button } from '@/components/ui/button'
import {
  AWS_NOTES,
  DEVELOPERS,
  PRINCIPLES,
  SERVICE_NOTES,
  STACK,
  githubUrl,
  initials,
} from '@/lib/about'
import { ROUTES } from '@/lib/navigation'

export const metadata: Metadata = {
  title: 'About',
  description:
    'How BidNow is built: eight Spring Boot microservices, Kafka events, live WebSocket updates, and the three-tier AWS deployment behind it.',
}

const FACTS = [
  { label: 'Backend modules',    value: '8' },
  { label: 'Database ownership', value: '1 per service' },
  { label: 'Service-to-service', value: 'Feign + Kafka' },
  { label: 'Live updates',       value: 'STOMP / WS' },
] as const

const DIAGRAM_FRAME =
  'overflow-x-auto rounded-xl border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]'

export default function AboutPage() {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        {/* Hero: always dark, like the homepage */}
        <section className="dark bg-[var(--color-brand-900)] text-foreground">
          <div className="mx-auto w-full max-w-[var(--container-xl)] px-4 pt-16 sm:px-6 md:pt-24">
            <p className="flex items-center gap-2.5 font-mono text-[length:var(--font-size-xs)] font-medium uppercase text-muted-foreground">
              <span className="size-2 rounded-full bg-[var(--color-warning-default)]" aria-hidden="true" />
              About BidNow
            </p>
            <h1 className="mt-6 max-w-4xl font-display text-[length:clamp(2.5rem,6vw,4.25rem)] font-medium leading-[1.05] text-balance">
              Real-time auctions, built as eight small services.
            </h1>
            <p className="mt-7 max-w-2xl text-[length:var(--font-size-md)] leading-relaxed text-muted-foreground text-pretty">
              BidNow is an English-auction marketplace. Every bid, anti-sniping extension and payout moves through an
              event-driven Spring Boot backend and reaches your screen over WebSocket. This page shows how the pieces
              fit, and where they will run on AWS.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Button
                variant="brand"
                size="xl"
                className="h-12 px-6"
                render={<Link href="#architecture" />}
                nativeButton={false}
              >
                See the architecture
                <ArrowRight className="size-4" aria-hidden="true" />
              </Button>
              <Button
                variant="outline"
                size="xl"
                className="h-12 border-white/35 bg-transparent px-6 text-white hover:bg-white/10"
                render={<Link href={ROUTES.browse} />}
                nativeButton={false}
              >
                Browse auctions
              </Button>
            </div>
            <dl className="mt-16 grid grid-cols-2 gap-x-6 border-t border-[var(--color-border-default)] md:mt-20 md:grid-cols-4">
              {FACTS.map(({ label, value }) => (
                <div key={label} className="pb-10 pt-6">
                  <dt className="font-mono text-[length:var(--font-size-xs)] uppercase text-muted-foreground">{label}</dt>
                  <dd className="mt-2 font-mono text-[length:var(--font-size-xl)] font-medium leading-tight sm:text-[length:var(--font-size-2xl)]">
                    {value}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        {/* Principles */}
        <Section id="principles">
          <SectionHeading eyebrow="Design principles" title="Four rules the whole system follows." />
          <ol className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {PRINCIPLES.map(({ n, title, body }) => (
              <li key={n} className="rounded-xl border border-[var(--color-border-default)] bg-card p-6">
                <span className="font-mono text-[length:var(--font-size-xs)] font-medium text-[var(--color-text-brand)]">{n}</span>
                <h3 className="mt-9 font-display text-[length:var(--font-size-lg)] font-medium leading-tight">{title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{body}</p>
              </li>
            ))}
          </ol>
        </Section>

        {/* Microservices */}
        <Section id="architecture" className="pt-0 md:pt-8">
          <SectionHeading
            eyebrow="Microservices"
            title="Eight modules, one request path."
            description="A Spring Boot 3.2 and Spring Cloud multi-module build. Requests enter through the gateway, services find each other through Eureka, and events fan out over Kafka."
          />
          <figure className="m-0">
            <div className={DIAGRAM_FRAME}>
              <ServiceMapDiagram />
            </div>
            <figcaption className="sr-only">BidNow microservice map</figcaption>
          </figure>
          <div className="mt-10 grid gap-8 md:grid-cols-3">
            {SERVICE_NOTES.map(({ title, body }) => (
              <div key={title}>
                <h3 className="font-display text-[length:var(--font-size-md)] font-medium">{title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{body}</p>
              </div>
            ))}
          </div>
        </Section>

        {/* AWS: always dark */}
        <div className="dark bg-[var(--color-bg-base)] text-foreground">
          <Section id="aws">
            <SectionHeading
              eyebrow="Target deployment on AWS"
              title="A classic three-tier layout, behind a CDN."
              description="Presentation, application and data tiers sit in separate subnets of one VPC. Traffic comes in through CloudFront and an Application Load Balancer; both compute tiers scale out in Auto Scaling Groups across two Availability Zones."
            />
            <figure className="m-0">
              <div className={DIAGRAM_FRAME}>
                <AwsArchitectureDiagram />
              </div>
              <figcaption className="sr-only">BidNow on AWS: three-tier architecture</figcaption>
            </figure>
            <div className="mt-10 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
              {AWS_NOTES.map(({ title, body }) => (
                <div key={title}>
                  <h3 className="font-display text-[length:var(--font-size-md)] font-medium">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{body}</p>
                </div>
              ))}
            </div>
          </Section>
        </div>

        {/* Tech stack */}
        <Section id="stack" tinted>
          <SectionHeading eyebrow="Tech stack" title="What it is built with." />
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {STACK.map(({ title, items }) => (
              <section key={title} aria-labelledby={`stack-${title}`} className="rounded-xl border border-[var(--color-border-default)] bg-card p-6">
                <h3 id={`stack-${title}`} className="font-display text-[length:var(--font-size-lg)] font-medium">
                  {title}
                </h3>
                <ul className="mt-4">
                  {items.map(({ name, role }) => (
                    <li
                      key={name}
                      className="flex justify-between gap-4 border-t border-[var(--color-border-default)] py-2.5 text-sm"
                    >
                      <span className="font-medium">{name}</span>
                      <span className="text-right text-muted-foreground">{role}</span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </Section>

        {/* Team */}
        <Section id="team">
          <SectionHeading eyebrow="Developers" title="Built by" />
          <ul className="grid max-w-3xl gap-4 sm:grid-cols-2">
            {DEVELOPERS.map(({ name, handle }) => (
              <li key={handle}>
                <a
                  href={githubUrl(handle)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="group flex items-center gap-4 rounded-xl border border-[var(--color-border-default)] bg-card px-6 py-5 transition-[border-color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:border-[var(--color-text-brand)]"
                >
                  <span
                    aria-hidden="true"
                    className="flex size-12 shrink-0 items-center justify-center rounded-full bg-accent font-display text-lg font-medium text-accent-foreground"
                  >
                    {initials(name)}
                  </span>
                  <span className="flex min-w-0 flex-col">
                    <span className="font-display text-[length:var(--font-size-lg)] font-medium">{name}</span>
                    <span className="flex items-center gap-1 truncate font-mono text-[length:var(--font-size-xs)] text-[var(--color-text-brand)]">
                      github.com/{handle}
                      <ArrowUpRight className="size-3.5 shrink-0" aria-hidden="true" />
                      <span className="sr-only">(opens in a new tab)</span>
                    </span>
                  </span>
                </a>
              </li>
            ))}
          </ul>
        </Section>
      </main>
      <Footer />
    </>
  )
}
