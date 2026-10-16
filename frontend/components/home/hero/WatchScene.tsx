'use client'

import { useEffect, useLayoutEffect, useMemo, useRef, type RefObject } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { Environment, Float, Lightformer, RoundedBox, Sparkles } from '@react-three/drei'
import {
  CanvasTexture,
  Color,
  MathUtils,
  Object3D,
  SRGBColorSpace,
  type Group,
  type InstancedMesh,
  type Mesh,
  type MeshBasicMaterial,
} from 'three'
import {
  RING_SEGMENTS,
  countdownHandAngles,
  litSegments,
  ringTone,
  wallClockHandAngles,
  type RingTone,
} from '@/lib/hero-watch'

/** Pointer position normalised to -1..1 across the window; written by `WatchStage`, read every frame. */
export interface PointerState {
  x: number
  y: number
}

export interface WatchSceneProps {
  /** The lot's end time. With none, the hands show the wall clock and the ring stays full. */
  endsAt:   Date | null
  /** Changes on every new bid; punches the watch and sends a shock ring out. */
  pulseKey: number
  /** Changes when the auction end time moves (anti-sniping); spins the watch once. */
  extendKey: number
  pointer:  RefObject<PointerState>
  paused:   boolean
}

const TWO_PI = Math.PI * 2

/*
 * WebGL cannot read CSS variables, so these mirror the design tokens:
 * brand-300 / brand-800 / brand-900, warning-default and danger-default (dark theme values).
 */
const COLORS = {
  faceHighlight: '#2E2A80',
  faceBase:      '#13113B',
  ink:           '#E4E4FB',
  dim:           '#25244A',
  tones: {
    calm:     '#8483F5',
    warning:  '#F59E0B',
    critical: '#EF4444',
    closed:   '#6B6B82',
  } satisfies Record<RingTone, string>,
} as const

const RING_RADIUS = 1.22
const FACE_Z = 0.215

/** Draws the dial (guilloche rings, minute track, indices, wordmark) once into a canvas texture. */
function createFaceTexture(): CanvasTexture {
  const size = 1024
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  texture.anisotropy = 8

  const ctx = canvas.getContext('2d')
  if (!ctx) return texture

  const c = size / 2
  const bg = ctx.createRadialGradient(c * 0.7, c * 0.6, 24, c, c, c)
  bg.addColorStop(0, COLORS.faceHighlight)
  bg.addColorStop(1, COLORS.faceBase)
  ctx.fillStyle = bg
  ctx.beginPath()
  ctx.arc(c, c, c, 0, TWO_PI)
  ctx.fill()

  ctx.strokeStyle = 'rgba(255,255,255,0.05)'
  ctx.lineWidth = 2
  for (let r = 70; r < c * 0.66; r += 20) {
    ctx.beginPath()
    ctx.arc(c, c, r, 0, TWO_PI)
    ctx.stroke()
  }

  ctx.lineCap = 'round'
  for (let i = 0; i < 60; i += 1) {
    const major = i % 5 === 0
    const a = (i / 60) * TWO_PI
    const outer = c * 0.8
    const inner = major ? c * 0.68 : c * 0.75
    ctx.strokeStyle = major ? COLORS.ink : 'rgba(228,228,251,0.45)'
    ctx.lineWidth = major ? 12 : 4
    ctx.beginPath()
    ctx.moveTo(c + Math.sin(a) * inner, c - Math.cos(a) * inner)
    ctx.lineTo(c + Math.sin(a) * outer, c - Math.cos(a) * outer)
    ctx.stroke()
  }

  ctx.fillStyle = COLORS.ink
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.font = '500 58px system-ui, sans-serif'
  ctx.fillText('BidNow', c, c * 0.62)
  ctx.fillStyle = 'rgba(228,228,251,0.55)'
  ctx.font = '500 30px system-ui, sans-serif'
  ctx.fillText('LIVE AUCTION', c, c * 1.42)

  return texture
}

function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3
}

function Watch({ endsAt, pulseKey, extendKey, pointer }: Omit<WatchSceneProps, 'paused'>) {
  const tiltRef   = useRef<Group>(null)
  const spinRef   = useRef<Group>(null)
  const hourRef   = useRef<Group>(null)
  const minuteRef = useRef<Group>(null)
  const secondRef = useRef<Group>(null)
  const ledsRef   = useRef<InstancedMesh>(null)
  const shockRef  = useRef<Mesh>(null)
  const shockMat  = useRef<MeshBasicMaterial>(null)

  const pulse = useRef(0)
  const spin = useRef(1)
  const lastLit = useRef(-1)
  const lastTone = useRef<RingTone | null>(null)
  const seenPulse = useRef(pulseKey)
  const seenExtend = useRef(extendKey)

  const faceTexture = useMemo(() => createFaceTexture(), [])
  useEffect(() => () => faceTexture.dispose(), [faceTexture])

  // Lay the 60 LED segments around the dial once.
  useLayoutEffect(() => {
    const mesh = ledsRef.current
    if (!mesh) return
    const dummy = new Object3D()
    for (let i = 0; i < RING_SEGMENTS; i += 1) {
      const a = (i / RING_SEGMENTS) * TWO_PI
      dummy.position.set(Math.sin(a) * RING_RADIUS, Math.cos(a) * RING_RADIUS, FACE_Z + 0.02)
      dummy.rotation.set(0, 0, -a)
      dummy.updateMatrix()
      mesh.setMatrixAt(i, dummy.matrix)
    }
    mesh.instanceMatrix.needsUpdate = true
  }, [])

  useEffect(() => {
    if (pulseKey !== seenPulse.current) {
      seenPulse.current = pulseKey
      pulse.current = 1
    }
  }, [pulseKey])

  useEffect(() => {
    if (extendKey !== seenExtend.current) {
      seenExtend.current = extendKey
      spin.current = 0
      pulse.current = 1
    }
  }, [extendKey])

  const dim = useMemo(() => new Color(COLORS.dim), [])
  const toneColors = useMemo(
    () => ({
      calm:     new Color(COLORS.tones.calm),
      warning:  new Color(COLORS.tones.warning),
      critical: new Color(COLORS.tones.critical),
      closed:   new Color(COLORS.tones.closed),
    }),
    [],
  )

  useFrame((_, delta) => {
    const tilt = tiltRef.current
    if (!tilt) return

    // Lean toward the cursor.
    const p = pointer.current
    tilt.rotation.x = MathUtils.damp(tilt.rotation.x, -p.y * 0.32, 4, delta)
    tilt.rotation.y = MathUtils.damp(tilt.rotation.y, p.x * 0.5, 4, delta)

    // One full turn when anti-sniping moves the end time.
    if (spinRef.current) {
      spin.current = Math.min(1, spin.current + delta / 1.4)
      spinRef.current.rotation.y = spin.current >= 1 ? 0 : easeOutCubic(spin.current) * TWO_PI
    }

    // New bid: the watch swells and a ring expands from the case.
    pulse.current = MathUtils.damp(pulse.current, 0, 3.2, delta)
    tilt.scale.setScalar(1 + pulse.current * 0.06)
    if (shockRef.current && shockMat.current) {
      shockRef.current.scale.setScalar(1 + (1 - pulse.current) * 0.9)
      shockMat.current.opacity = pulse.current * 0.85
    }

    // Hands and LED ring follow the countdown.
    const secondsLeft = endsAt ? Math.max(0, (endsAt.getTime() - Date.now()) / 1000) : Number.POSITIVE_INFINITY
    const angles = endsAt ? countdownHandAngles(secondsLeft) : wallClockHandAngles(new Date())
    if (hourRef.current)   hourRef.current.rotation.z   = -angles.hour
    if (minuteRef.current) minuteRef.current.rotation.z = -angles.minute
    if (secondRef.current) secondRef.current.rotation.z = -angles.second

    const lit = litSegments(secondsLeft)
    const tone = ringTone(secondsLeft)
    const leds = ledsRef.current
    if (leds && (lit !== lastLit.current || tone !== lastTone.current)) {
      lastLit.current = lit
      lastTone.current = tone
      for (let i = 0; i < RING_SEGMENTS; i += 1) {
        leds.setColorAt(i, i < lit ? toneColors[tone] : dim)
      }
      if (leds.instanceColor) leds.instanceColor.needsUpdate = true
    }
  })

  return (
    <group scale={0.92}>
      <group ref={tiltRef}>
        <group ref={spinRef}>
          <Float speed={1.6} rotationIntensity={0.18} floatIntensity={0.5} floatingRange={[-0.05, 0.05]}>
            {/* strap */}
            <RoundedBox args={[1.0, 1.5, 0.16]} radius={0.07} smoothness={4} position={[0, 2.0, -0.06]} rotation={[0.1, 0, 0]}>
              <meshStandardMaterial color="#1C1B4B" roughness={0.55} metalness={0.1} />
            </RoundedBox>
            <RoundedBox args={[1.0, 1.5, 0.16]} radius={0.07} smoothness={4} position={[0, -2.0, -0.06]} rotation={[-0.1, 0, 0]}>
              <meshStandardMaterial color="#1C1B4B" roughness={0.55} metalness={0.1} />
            </RoundedBox>

            {/* case, bezel, crown */}
            <mesh rotation={[Math.PI / 2, 0, 0]}>
              <cylinderGeometry args={[1.5, 1.5, 0.4, 96]} />
              <meshStandardMaterial color="#3B3A7A" metalness={0.95} roughness={0.28} envMapIntensity={1.4} />
            </mesh>
            <mesh position={[0, 0, 0.2]}>
              <torusGeometry args={[1.42, 0.1, 32, 128]} />
              <meshStandardMaterial color="#C7C6FB" metalness={1} roughness={0.18} envMapIntensity={1.6} />
            </mesh>
            <mesh position={[1.58, 0, 0]} rotation={[0, 0, Math.PI / 2]}>
              <cylinderGeometry args={[0.13, 0.13, 0.28, 32]} />
              <meshStandardMaterial color="#C7C6FB" metalness={1} roughness={0.3} envMapIntensity={1.4} />
            </mesh>

            {/* dial */}
            <mesh position={[0, 0, FACE_Z - 0.005]}>
              <circleGeometry args={[1.34, 96]} />
              <meshStandardMaterial map={faceTexture} roughness={0.6} metalness={0.15} />
            </mesh>

            {/* countdown LED ring: lit segments are the time left in the final five minutes */}
            <instancedMesh ref={ledsRef} args={[undefined, undefined, RING_SEGMENTS]}>
              <boxGeometry args={[0.045, 0.13, 0.03]} />
              <meshBasicMaterial toneMapped={false} />
            </instancedMesh>

            {/* hands */}
            <group ref={hourRef} position={[0, 0, 0.25]}>
              <mesh position={[0, 0.3, 0]}>
                <boxGeometry args={[0.1, 0.72, 0.03]} />
                <meshStandardMaterial color="#E4E4FB" metalness={0.8} roughness={0.25} envMapIntensity={1.4} />
              </mesh>
            </group>
            <group ref={minuteRef} position={[0, 0, 0.275]}>
              <mesh position={[0, 0.42, 0]}>
                <boxGeometry args={[0.07, 1.0, 0.03]} />
                <meshStandardMaterial color="#E4E4FB" metalness={0.8} roughness={0.25} envMapIntensity={1.4} />
              </mesh>
            </group>
            <group ref={secondRef} position={[0, 0, 0.3]}>
              <mesh position={[0, 0.45, 0]}>
                <boxGeometry args={[0.025, 1.15, 0.02]} />
                <meshBasicMaterial color="#F59E0B" toneMapped={false} />
              </mesh>
              <mesh position={[0, -0.22, 0]}>
                <boxGeometry args={[0.04, 0.3, 0.02]} />
                <meshBasicMaterial color="#F59E0B" toneMapped={false} />
              </mesh>
            </group>
            <mesh position={[0, 0, 0.32]} rotation={[Math.PI / 2, 0, 0]}>
              <cylinderGeometry args={[0.07, 0.07, 0.06, 32]} />
              <meshStandardMaterial color="#F59E0B" metalness={0.6} roughness={0.3} />
            </mesh>

            {/* glass */}
            <mesh position={[0, 0, 0.34]}>
              <circleGeometry args={[1.34, 96]} />
              <meshPhysicalMaterial
                transparent
                opacity={0.1}
                roughness={0}
                metalness={0}
                clearcoat={1}
                envMapIntensity={3}
                depthWrite={false}
              />
            </mesh>

            {/* shock ring, expanded on every new bid */}
            <mesh ref={shockRef} position={[0, 0, 0.1]}>
              <torusGeometry args={[1.5, 0.02, 16, 128]} />
              <meshBasicMaterial ref={shockMat} color="#A5A4F8" transparent opacity={0} toneMapped={false} depthWrite={false} />
            </mesh>
          </Float>
        </group>
      </group>
    </group>
  )
}

export default function WatchScene({ paused, ...rest }: WatchSceneProps) {
  return (
    <Canvas
      dpr={[1, 1.75]}
      camera={{ position: [0, 0, 7.6], fov: 30 }}
      frameloop={paused ? 'never' : 'always'}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
    >
      <ambientLight intensity={0.35} />
      <directionalLight position={[3, 4, 5]} intensity={1.1} />
      {/* Procedural studio lighting: no HDR download, so the scene works offline and costs no request. */}
      <Environment resolution={256} frames={1}>
        <Lightformer form="rect" intensity={4}   position={[0, 5, 3]}  scale={[8, 3, 1]} color="#ffffff" />
        <Lightformer form="rect" intensity={2.5} position={[-5, 1, 2]} scale={[2, 6, 1]} rotation-y={Math.PI / 4}  color="#8483F5" />
        <Lightformer form="rect" intensity={2}   position={[5, -1, 2]} scale={[2, 6, 1]} rotation-y={-Math.PI / 4} color="#F59E0B" />
        <Lightformer form="ring" intensity={2}   position={[0, 0, -4]} scale={4} color="#6366F1" />
      </Environment>
      <Sparkles count={46} scale={[7, 5, 3]} size={2.2} speed={0.35} opacity={0.7} color="#FCD34D" position={[0, 0, -0.6]} />
      <Watch {...rest} />
    </Canvas>
  )
}
