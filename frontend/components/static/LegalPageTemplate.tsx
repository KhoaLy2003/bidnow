import Link from 'next/link'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { TableOfContents } from '@/components/static/TableOfContents'
import { Button } from '@/components/ui/button'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type LegalBlock =
  | { type: 'para'; text: string }
  | { type: 'bullets'; items: string[] }
  | { type: 'numbered'; items: string[] }
  | { type: 'table'; headers: string[]; rows: string[][] }
  | { type: 'callout'; tone?: 'indigo' | 'amber' | 'rose' | 'green'; title: string; body: string }

export interface LegalSection {
  title: string
  blocks: LegalBlock[]
}

export interface LegalDocument {
  eyebrow: string
  title: string
  intro?: string
  version?: string
  updated: string
  contact?: string
  meta: Array<{ k: string; v: string }>
  sections: LegalSection[]
}

export interface RelatedDoc {
  tag: string
  title: string
  sub: string
  href: string
}

// ---------------------------------------------------------------------------
// Block renderers
// ---------------------------------------------------------------------------

function Para({ text }: { text: string }) {
  return (
    <p
      style={{
        fontSize: 15,
        lineHeight: 1.7,
        color: 'var(--color-text-secondary)',
        marginTop: 14,
        marginBottom: 0,
      }}
    >
      {text}
    </p>
  )
}

function BulletList({ items, numbered }: { items: string[]; numbered?: boolean }) {
  return (
    <div className="flex flex-col mt-3.5" style={{ gap: 10 }}>
      {items.map((item, i) => (
        <div key={i} className="flex items-start gap-3">
          <span
            className="font-mono shrink-0 text-right"
            style={{
              width: 22,
              fontSize: 12,
              color: 'var(--color-text-tertiary)',
              lineHeight: 1.7,
              paddingTop: 1,
            }}
          >
            {numbered ? String(i + 1).padStart(2, '0') : '·'}
          </span>
          <span
            style={{ fontSize: 15, lineHeight: 1.65, color: 'var(--color-text-secondary)', flex: 1 }}
          >
            {item}
          </span>
        </div>
      ))}
    </div>
  )
}

const CALLOUT_TONES = {
  indigo: {
    bg: 'var(--color-brand-50)',
    fg: 'var(--color-brand-700)',
    border: 'var(--brand-200)',
  },
  amber: {
    bg: 'var(--color-auction-ending-bg)',
    fg: 'var(--color-auction-ending-text)',
    border: 'var(--color-auction-ending-border)',
  },
  rose: {
    bg: 'var(--color-auction-critical-bg)',
    fg: 'var(--color-auction-critical-text)',
    border: 'var(--color-auction-critical-border)',
  },
  green: {
    bg: 'var(--color-auction-won-bg)',
    fg: 'var(--color-auction-won-text)',
    border: 'var(--color-auction-won-border)',
  },
} as const

function Callout({
  tone = 'indigo',
  title,
  body,
}: {
  tone?: 'indigo' | 'amber' | 'rose' | 'green'
  title: string
  body: string
}) {
  const t = CALLOUT_TONES[tone]
  return (
    <div
      style={{
        marginTop: 18,
        padding: '14px 18px',
        background: t.bg,
        border: `1px solid ${t.border}`,
        borderRadius: 8,
      }}
    >
      <div className="font-medium text-sm" style={{ color: t.fg }}>
        {title}
      </div>
      <div className="text-sm mt-1" style={{ color: t.fg, lineHeight: 1.6, opacity: 0.92 }}>
        {body}
      </div>
    </div>
  )
}

function DataTable({ headers, rows }: { readonly headers: readonly string[]; readonly rows: readonly (readonly string[])[] }) {
  return (
    <div
      style={{
        marginTop: 16,
        border: '1px solid var(--color-border-default)',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div
        className="flex"
        style={{
          background: 'var(--color-bg-elevated)',
          borderBottom: '1px solid var(--color-border-default)',
          padding: '11px 16px',
        }}
      >
        {headers.map((h, i) => (
          <span
            key={i}
            className="font-mono"
            style={{
              flex: 1,
              fontSize: 10.5,
              color: 'var(--color-text-tertiary)',
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
            }}
          >
            {h}
          </span>
        ))}
      </div>
      {rows.map((row, i) => (
        <div
          key={i}
          className="flex"
          style={{
            padding: '13px 16px',
            borderBottom: i < rows.length - 1 ? '1px solid var(--color-border-default)' : '0',
            fontSize: 13.5,
            lineHeight: 1.55,
          }}
        >
          {row.map((cell, j) => (
            <span
              key={headers[j]}
              style={{
                flex: 1,
                color: j === 0 ? 'var(--color-text-primary)' : 'var(--color-text-secondary)',
              }}
            >
              {cell}
            </span>
          ))}
        </div>
      ))}
    </div>
  )
}

function renderBlock(block: LegalBlock, i: number) {
  if (block.type === 'para') return <Para key={i} text={block.text} />
  if (block.type === 'bullets') return <BulletList key={i} items={block.items} />
  if (block.type === 'numbered') return <BulletList key={i} items={block.items} numbered />
  if (block.type === 'table') return <DataTable key={i} headers={block.headers} rows={block.rows} />
  if (block.type === 'callout')
    return <Callout key={i} tone={block.tone} title={block.title} body={block.body} />
  return null
}

function slugify(s: string) {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
}

// ---------------------------------------------------------------------------
// Main template
// ---------------------------------------------------------------------------

interface LegalPageTemplateProps {
  readonly doc: LegalDocument
  readonly related?: readonly RelatedDoc[]
}

export function LegalPageTemplate({ doc, related = [] }: LegalPageTemplateProps) {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1 bg-background">
        {/* Hero strip */}
        <div
          className="mx-auto w-full px-6"
          style={{ maxWidth: 1200, paddingTop: 56, paddingBottom: 28 }}
        >
          {/* Breadcrumb */}
          <div className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
            <span>Legal</span>
            <span style={{ color: 'var(--color-text-disabled)' }}>›</span>
            <span>{doc.eyebrow}</span>
            <span style={{ color: 'var(--color-text-disabled)' }}>›</span>
            <span style={{ color: 'var(--color-text-secondary)' }}>{doc.title}</span>
          </div>

          {/* Eyebrow */}
          <span
            className="font-mono font-medium uppercase"
            style={{
              fontSize: 11,
              color: 'var(--color-brand-600)',
              letterSpacing: '0.1em',
            }}
          >
            {doc.eyebrow}
          </span>

          {/* Title */}
          <h1
            className="font-display font-medium mt-3"
            style={{
              fontSize: 52,
              lineHeight: 1.05,
              letterSpacing: '-0.02em',
              color: 'var(--color-text-primary)',
              maxWidth: 880,
            }}
          >
            {doc.title}
          </h1>

          {doc.intro && (
            <p
              className="mt-3"
              style={{
                fontSize: 17,
                lineHeight: 1.55,
                color: 'var(--color-text-secondary)',
                maxWidth: 760,
              }}
            >
              {doc.intro}
            </p>
          )}

          {/* Meta strip */}
          <div
            className="flex flex-wrap items-center gap-6 mt-4 pt-3.5 pb-3.5"
            style={{
              borderTop: '1px solid var(--color-border-default)',
              borderBottom: '1px solid var(--color-border-default)',
            }}
          >
            {doc.meta.map((m) => (
              <div key={m.k} className="flex flex-col">
                <span
                  className="font-mono uppercase"
                  style={{
                    fontSize: 10,
                    color: 'var(--color-text-tertiary)',
                    letterSpacing: '0.08em',
                  }}
                >
                  {m.k}
                </span>
                <span className="text-sm font-medium" style={{ color: 'var(--color-text-primary)' }}>
                  {m.v}
                </span>
              </div>
            ))}
            <div className="ml-auto flex gap-2">
              <Button variant="outline" size="lg" disabled title="PDF export coming soon">⤓ PDF</Button>
              <Button variant="outline" size="lg" disabled title="Version history coming soon">Version history</Button>
            </div>
          </div>
        </div>

        {/* Two-column body */}
        <div
          className="mx-auto w-full px-6 pb-24"
          style={{ maxWidth: 1200, paddingTop: 16 }}
        >
          <div
            style={{ display: 'grid', gridTemplateColumns: '240px 1fr', gap: 64, alignItems: 'start' }}
          >
            {/* TOC */}
            <TableOfContents sections={doc.sections} contact={doc.contact} />

            {/* Sections */}
            <div className="flex flex-col" style={{ maxWidth: 760 }}>
              {doc.sections.map((s, i) => (
                <section key={i} id={slugify(s.title)} style={{ paddingTop: 48 }}>
                  <div className="flex items-baseline gap-3 mb-1.5">
                    <span
                      className="font-mono font-medium"
                      style={{ fontSize: 12, color: 'var(--color-brand-600)' }}
                    >
                      {String(i + 1).padStart(2, '0')}
                    </span>
                    <h2
                      className="font-display font-medium"
                      style={{
                        margin: 0,
                        fontSize: 26,
                        letterSpacing: '-0.015em',
                        color: 'var(--color-text-primary)',
                      }}
                    >
                      {s.title}
                    </h2>
                  </div>
                  {s.blocks.map((b, j) => renderBlock(b, j))}
                </section>
              ))}

              {/* Sign-off footer */}
              <div
                className="flex flex-wrap items-center justify-between gap-3 mt-16 pt-5"
                style={{ borderTop: '1px solid var(--color-border-default)' }}
              >
                <span
                  className="text-xs"
                  style={{ color: 'var(--color-text-tertiary)' }}
                >
                  You&apos;re reading version{' '}
                  <span className="font-mono" style={{ color: 'var(--color-text-primary)' }}>
                    {doc.version ?? '1.0'}
                  </span>{' '}
                  · last updated{' '}
                  <span className="font-mono" style={{ color: 'var(--color-text-primary)' }}>
                    {doc.updated}
                  </span>
                  .
                </span>
                <div className="flex gap-2">
                  <Button variant="outline" size="lg">Was this page helpful?</Button>
                </div>
              </div>

              {/* Related docs */}
              {related.length > 0 && (
                <div className="flex flex-col gap-3 mt-12">
                  <span
                    className="font-mono font-medium uppercase"
                    style={{
                      fontSize: 11,
                      color: 'var(--color-brand-600)',
                      letterSpacing: '0.1em',
                    }}
                  >
                    Related documents
                  </span>
                  <div className="grid grid-cols-3 gap-3.5">
                    {related.map((r, i) => (
                      <Link
                        key={i}
                        href={r.href}
                        className="flex flex-col gap-2 p-4.5 rounded-xl border border-[var(--color-border-default)] bg-card hover:border-[var(--color-brand-600)] transition-colors no-underline"
                      >
                        <span
                          className="font-mono uppercase inline-flex items-center"
                          style={{
                            fontSize: 10.5,
                            color: 'var(--color-text-secondary)',
                            border: '1px solid var(--color-border-default)',
                            borderRadius: 4,
                            padding: '3px 8px',
                            letterSpacing: '0.06em',
                          }}
                        >
                          {r.tag}
                        </span>
                        <span
                          className="text-sm font-medium"
                          style={{ color: 'var(--color-text-primary)' }}
                        >
                          {r.title}
                        </span>
                        <span className="text-xs" style={{ color: 'var(--color-text-tertiary)' }}>
                          {r.sub}
                        </span>
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
