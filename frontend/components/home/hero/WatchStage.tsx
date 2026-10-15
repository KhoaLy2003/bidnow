'use client'

import { Component, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import dynamic from 'next/dynamic'
import type { PointerState } from './WatchScene'

interface WatchStageProps {
  endsAt:    Date | null
  pulseKey:  number
  extendKey: number
}

/** Static dial shown while the 3D scene loads, and kept for reduced motion, no WebGL, or a failed scene. */
function WatchPoster() {
  const ticks = Array.from({ length: 60 }, (_, i) => i)
  return (
    <div className="grid size-full place-items-center" aria-hidden="true">
      <svg viewBox="0 0 400 400" className="w-[78%] max-w-[420px] text-[var(--color-brand-300)]">
        <circle cx="200" cy="200" r="196" fill="var(--color-brand-800)" stroke="currentColor" strokeOpacity="0.5" strokeWidth="4" />
        <circle cx="200" cy="200" r="150" fill="var(--color-brand-900)" stroke="currentColor" strokeOpacity="0.2" />
        {ticks.map((i) => {
          const major = i % 5 === 0
          const a = (i / 60) * Math.PI * 2
          const r1 = major ? 126 : 136
          return (
            <line
              key={i}
              x1={200 + Math.sin(a) * r1}
              y1={200 - Math.cos(a) * r1}
              x2={200 + Math.sin(a) * 150}
              y2={200 - Math.cos(a) * 150}
              stroke="white"
              strokeOpacity={major ? 0.9 : 0.35}
              strokeWidth={major ? 4 : 1.5}
              strokeLinecap="round"
            />
          )
        })}
        <path d="M200 200V120M200 200l44 26" stroke="white" strokeOpacity="0.9" strokeWidth="7" strokeLinecap="round" fill="none" />
        <circle cx="200" cy="200" r="7" fill="var(--color-warning-default)" />
      </svg>
    </div>
  )
}

const WatchScene = dynamic(() => import('./WatchScene'), {
  ssr: false,
  loading: () => <WatchPoster />,
})

class SceneBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  render() {
    return this.state.failed ? <WatchPoster /> : this.props.children
  }
}

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

let webglSupported: boolean | null = null
function hasWebGL(): boolean {
  if (webglSupported === null) {
    try {
      webglSupported = Boolean(document.createElement('canvas').getContext('webgl2'))
    } catch {
      webglSupported = false
    }
  }
  return webglSupported
}

function subscribeMotion(onChange: () => void) {
  const query = window.matchMedia(REDUCED_MOTION_QUERY)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

const canRender3D = () => !window.matchMedia(REDUCED_MOTION_QUERY).matches && hasWebGL()

/**
 * The hero's 3D watch. Loaded on the client only (three.js is ~150 KB we keep out of the initial
 * bundle), paused while off screen, and replaced by a static dial when 3D is not appropriate.
 */
export function WatchStage({ endsAt, pulseKey, extendKey }: WatchStageProps) {
  const enabled = useSyncExternalStore(subscribeMotion, canRender3D, () => false)
  const containerRef = useRef<HTMLDivElement>(null)
  const pointer = useRef<PointerState>({ x: 0, y: 0 })
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    const onMove = (e: PointerEvent) => {
      pointer.current.x = (e.clientX / window.innerWidth) * 2 - 1
      pointer.current.y = -((e.clientY / window.innerHeight) * 2 - 1)
    }
    window.addEventListener('pointermove', onMove, { passive: true })
    return () => window.removeEventListener('pointermove', onMove)
  }, [])

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const observer = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting), { threshold: 0 })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return (
    <div
      ref={containerRef}
      className="relative aspect-square w-full [mask-image:linear-gradient(to_bottom,transparent,black_12%,black_88%,transparent)]"
    >
      {enabled ? (
        <SceneBoundary>
          <WatchScene
            endsAt={endsAt}
            pulseKey={pulseKey}
            extendKey={extendKey}
            pointer={pointer}
            paused={!visible}
          />
        </SceneBoundary>
      ) : (
        <WatchPoster />
      )}
    </div>
  )
}
