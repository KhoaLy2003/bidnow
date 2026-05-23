'use client'

import { useState, useEffect, useMemo } from 'react'
import { Separator } from '@/components/ui/separator'

function slugify(s: string) {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
}

interface TableOfContentsProps {
  sections: Array<{ title: string }>
  contact?: string
}

export function TableOfContents({ sections, contact }: TableOfContentsProps) {
  const [activeId, setActiveId] = useState<string>(slugify(sections[0]?.title ?? ''))

  // Stable string key — avoids re-running the effect when the parent re-renders
  // with a new array reference but identical section titles
  const sectionKey = useMemo(() => sections.map(s => s.title).join('|'), [sections])

  useEffect(() => {
    const ids = sectionKey.split('|').map(slugify)
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting)
        if (visible.length > 0) {
          setActiveId(visible[0].target.id)
        }
      },
      { rootMargin: '-10% 0% -80% 0%', threshold: 0 },
    )

    ids.forEach((id) => {
      const el = document.getElementById(id)
      if (el) observer.observe(el)
    })

    return () => observer.disconnect()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sectionKey])

  const email = contact ?? 'support@bidnow.com'

  return (
    <nav className="sticky top-24 flex flex-col gap-3" aria-label="On this page">
      <span
        className="font-mono uppercase"
        style={{ fontSize: 10.5, color: 'var(--color-text-tertiary)', letterSpacing: '0.1em' }}
      >
        On this page
      </span>
      <div className="flex flex-col">
        {sections.map((s, i) => {
          const id = slugify(s.title)
          const isActive = activeId === id
          return (
            <a
              key={i}
              href={`#${id}`}
              className="flex items-baseline gap-2 no-underline transition-colors hover:text-[var(--color-text-primary)]"
              style={{
                padding: '7px 0 7px 12px',
                borderLeft: isActive
                  ? '2px solid var(--color-brand-600)'
                  : '2px solid var(--color-border-default)',
                marginLeft: -14,
                color: isActive ? 'var(--color-text-primary)' : 'var(--color-text-secondary)',
                fontWeight: isActive ? 500 : 400,
              }}
            >
              <span
                className="font-mono shrink-0"
                style={{ fontSize: 10.5, color: 'var(--color-text-tertiary)', width: 18 }}
              >
                {String(i + 1).padStart(2, '0')}
              </span>
              <span style={{ fontSize: 13, lineHeight: 1.4 }}>{s.title}</span>
            </a>
          )
        })}
      </div>

      <Separator className="my-1" />

      <span
        className="font-mono uppercase"
        style={{ fontSize: 10.5, color: 'var(--color-text-tertiary)', letterSpacing: '0.1em' }}
      >
        Need a human?
      </span>
      <p className="text-sm" style={{ color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
        Email{' '}
        <a
          href={`mailto:${email}`}
          className="font-mono"
          style={{ color: 'var(--color-text-primary)' }}
        >
          {email}
        </a>{' '}
        for clarifications.
      </p>
    </nav>
  )
}
