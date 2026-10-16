import type { CSSProperties } from 'react'

/** Line colours for the architecture diagrams, as design tokens so both themes work. */
export type Tone = 'traffic' | 'feign' | 'events' | 'discovery' | 'data'

const TONE_COLOR: Record<Tone, string> = {
  traffic:   'var(--color-text-brand)',
  feign:     'var(--color-info-default)',
  events:    'var(--color-warning-default)',
  discovery: 'var(--color-text-secondary)',
  data:      'var(--color-info-default)',
}

export type NodeVariant = 'card' | 'brand' | 'accent' | 'data' | 'events' | 'chip' | 'band' | 'zone'

interface VariantStyle {
  rect: CSSProperties
  text: string
  sub:  string
  mono: string
}

const VARIANTS: Record<NodeVariant, VariantStyle> = {
  card: {
    rect: { fill: 'var(--color-bg-card)', stroke: 'var(--color-border-strong)' },
    text: 'var(--color-text-primary)',
    sub:  'var(--color-text-secondary)',
    mono: 'var(--color-text-brand)',
  },
  brand: {
    rect: { fill: 'var(--color-brand-600)', stroke: 'var(--color-brand-600)' },
    text: '#FFFFFF',
    sub:  '#FFFFFF',
    mono: '#FFFFFF',
  },
  accent: {
    rect: { fill: 'var(--color-bg-card)', stroke: 'var(--color-text-brand)' },
    text: 'var(--color-text-primary)',
    sub:  'var(--color-text-secondary)',
    mono: 'var(--color-text-brand)',
  },
  data: {
    rect: { fill: 'var(--color-bg-card)', stroke: 'var(--color-info-default)' },
    text: 'var(--color-text-primary)',
    sub:  'var(--color-text-secondary)',
    mono: 'var(--color-text-brand)',
  },
  events: {
    rect: { fill: 'var(--color-warning-subtle)', stroke: 'var(--color-warning-border)' },
    text: 'var(--color-warning-text)',
    sub:  'var(--color-warning-text)',
    mono: 'var(--color-warning-text)',
  },
  chip: {
    rect: { fill: 'var(--color-bg-base)', stroke: 'var(--color-border-default)' },
    text: 'var(--color-text-primary)',
    sub:  'var(--color-text-secondary)',
    mono: 'var(--color-text-secondary)',
  },
  band: {
    rect: { fill: 'var(--color-bg-base)', stroke: 'var(--color-border-default)' },
    text: 'var(--color-text-secondary)',
    sub:  'var(--color-text-secondary)',
    mono: 'var(--color-text-secondary)',
  },
  zone: {
    rect: { fill: 'var(--color-accent)', stroke: 'var(--color-text-brand)', strokeDasharray: '5 4' },
    text: 'var(--color-text-brand)',
    sub:  'var(--color-text-brand)',
    mono: 'var(--color-text-brand)',
  },
}

interface Row {
  text: string
  size: number
  line: number
  mono?: boolean
  weight?: number
  port?: string
  fill: 'text' | 'sub' | 'mono'
}

interface NodeProps {
  x: number
  y: number
  w: number
  h: number
  /** One title line, or several (the port goes after the last). */
  title?:   string | readonly string[]
  /** Port or other mono suffix shown after the title. */
  port?:    string
  lines?:   readonly string[]
  mono?:    string
  variant?: NodeVariant
  titleSize?: number
}

/** A rounded card with centred text rows (title, description lines, optional mono line). */
export function Node({ x, y, w, h, title, port, lines = [], mono, variant = 'card', titleSize = 15 }: NodeProps) {
  const v = VARIANTS[variant]
  const rows: Row[] = []
  const titleLines = title === undefined ? [] : typeof title === 'string' ? [title] : title
  titleLines.forEach((text, i) => {
    const last = i === titleLines.length - 1
    rows.push({ text, size: titleSize, line: 20, weight: 500, port: last ? port : undefined, fill: 'text' })
  })
  for (const text of lines) rows.push({ text, size: 12, line: 16, fill: 'sub' })
  if (mono) rows.push({ text: mono, size: 11, line: 16, mono: true, fill: 'mono' })

  const total = rows.reduce((sum, r) => sum + r.line, 0)
  let cursor = y + (h - total) / 2
  const cx = x + w / 2

  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={8} strokeWidth={1} style={v.rect} />
      {rows.map((r, i) => {
        const baseline = cursor + r.size
        cursor += r.line
        return (
          <text
            key={`${r.text}-${i}`}
            x={cx}
            y={baseline}
            textAnchor="middle"
            fontSize={r.size}
            fontWeight={r.weight}
            className={r.mono ? 'font-mono' : undefined}
            style={{ fill: v[r.fill] }}
          >
            {r.text}
            {r.port && (
              <tspan dx={6} fontSize={11} fontWeight={400} className="font-mono" style={{ fill: v.sub }}>
                {r.port}
              </tspan>
            )}
          </text>
        )
      })}
    </g>
  )
}

interface LabelProps {
  x: number
  y: number
  text: string
  anchor?: 'start' | 'middle' | 'end'
  size?: number
  mono?: boolean
  fill?: string
}

/** Free text on the diagram (arrow captions, zone titles). `y` is the baseline. */
export function Label({ x, y, text, anchor = 'start', size = 13, mono = false, fill = 'var(--color-text-secondary)' }: LabelProps) {
  return (
    <text x={x} y={y} textAnchor={anchor} fontSize={size} className={mono ? 'font-mono' : undefined} style={{ fill }}>
      {text}
    </text>
  )
}

/** Arrowhead markers, one per tone. `prefix` keeps ids unique when several diagrams share a page. */
export function ArrowMarkers({ prefix }: { prefix: string }) {
  return (
    <defs>
      {(Object.keys(TONE_COLOR) as Tone[]).map((tone) => (
        <marker
          key={tone}
          id={`${prefix}-${tone}`}
          viewBox="0 0 8 8"
          refX={7}
          refY={4}
          markerWidth={8}
          markerHeight={8}
          markerUnits="userSpaceOnUse"
          orient="auto-start-reverse"
        >
          <path d="M0 0 L8 4 L0 8 Z" style={{ fill: TONE_COLOR[tone] }} />
        </marker>
      ))}
    </defs>
  )
}

interface ArrowProps {
  prefix:  string
  tone:    Tone
  /** SVG path. The last point is the tip when `head` is set. */
  d:       string
  dashed?: boolean
  /** Head at the end of the path (default) or none for a plain line. */
  head?:   boolean
  /** Also a head at the start. */
  both?:   boolean
}

export function Arrow({ prefix, tone, d, dashed = false, head = true, both = false }: ArrowProps) {
  const marker = `url(#${prefix}-${tone})`
  return (
    <path
      d={d}
      fill="none"
      strokeWidth={2}
      strokeLinejoin="round"
      strokeDasharray={dashed ? '6 6' : undefined}
      markerEnd={head ? marker : undefined}
      markerStart={both ? marker : undefined}
      style={{ stroke: TONE_COLOR[tone] }}
    />
  )
}

/** Short line sample for a legend entry. */
export function LegendLine({ x, y, tone, dashed = false }: { x: number; y: number; tone: Tone; dashed?: boolean }) {
  return (
    <path
      d={`M${x} ${y} H${x + 32}`}
      fill="none"
      strokeWidth={2}
      strokeDasharray={dashed ? '6 6' : undefined}
      style={{ stroke: TONE_COLOR[tone] }}
    />
  )
}
