import { Arrow, ArrowMarkers, Label, LegendLine, Node } from './diagram-parts'

const P = 'svc'

interface ServiceCard {
  x:     number
  name:  string
  port:  string
  lines: readonly string[]
  store: string
}

const CARD_W = 136
const SERVICES: readonly ServiceCard[] = [
  { x: 48,  name: 'identity', port: ':8081', lines: ['Register, login, OTP', 'JWT issuance'],        store: 'postgres' },
  { x: 232, name: 'user',     port: ':8082', lines: ['Profiles and', 'preferences'],                 store: 'postgres' },
  { x: 416, name: 'auction',  port: ':8083', lines: ['Create, edit, close', 'JobRunr jobs'],         store: 'postgres · redis' },
  { x: 600, name: 'bidding',  port: ':8084', lines: ['Place bid, auto-bid', 'anti-sniping'],         store: 'postgres · redis' },
  { x: 784, name: 'wallet',   port: ':8085', lines: ['Balances, deposits', 'escrow ledger'],         store: 'postgres' },
  { x: 968, name: 'media',    port: ':8086', lines: ['Email, S3 uploads', 'audit log, live push'],   store: 'postgres · redis' },
]

/** The eight backend modules and how requests, Feign calls and Kafka events move between them. */
export function ServiceMapDiagram() {
  return (
    <svg
      viewBox="0 0 1200 672"
      role="img"
      aria-labelledby="svc-title svc-desc"
      className="block h-auto w-full min-w-[1000px] font-sans"
    >
      <title id="svc-title">BidNow microservice map</title>
      <desc id="svc-desc">
        The Next.js frontend calls the API gateway over REST and STOMP. The gateway routes to the identity, user,
        auction, bidding, wallet and media services, finding them through the Eureka registry. Bidding calls
        auction and wallet with Feign, and identity calls user. Every service publishes and consumes events on
        Apache Kafka.
      </desc>
      <ArrowMarkers prefix={P} />

      {/* legend */}
      <LegendLine x={48} y={56} tone="traffic" />
      <LegendLine x={48} y={80} tone="feign" />
      <LegendLine x={48} y={104} tone="events" dashed />
      <LegendLine x={48} y={128} tone="discovery" dashed />
      <Label x={92} y={60} text="HTTP request path" />
      <Label x={92} y={84} text="Synchronous call (OpenFeign)" />
      <Label x={92} y={108} text="Kafka events, both directions" />
      <Label x={92} y={132} text="Service discovery (Eureka)" />

      {/* request path */}
      <Arrow prefix={P} tone="traffic" d="M576 120 V192" />
      <Arrow prefix={P} tone="discovery" dashed d="M436 236 H248" />
      <Arrow prefix={P} tone="traffic" head={false} d="M576 280 V324" />
      <Arrow prefix={P} tone="traffic" head={false} d="M116 324 H1036" />
      {SERVICES.map((s) => (
        <Arrow key={`in-${s.name}`} prefix={P} tone="traffic" d={`M${s.x + CARD_W / 2} 324 V368`} />
      ))}

      {/* synchronous calls */}
      <Arrow prefix={P} tone="feign" d="M184 428 H232" />
      <Arrow prefix={P} tone="feign" d="M600 428 H552" />
      <Arrow prefix={P} tone="feign" d="M736 428 H784" />

      {/* Kafka */}
      {SERVICES.map((s) => (
        <Arrow key={`kafka-${s.name}`} prefix={P} tone="events" dashed both d={`M${s.x + CARD_W / 2} 488 V560`} />
      ))}

      <Node x={456} y={48} w={240} h={72} title="Next.js frontend" lines={['Browser · React 19 · Tailwind CSS']} />
      <Node x={436} y={192} w={280} h={88} variant="brand" title="API Gateway" port=":8080" lines={['JWT validation · routing · CORS', 'aggregated Swagger UI']} />
      <Node x={48} y={192} w={200} h={88} title="Discovery" port=":8761" lines={['Eureka service registry']} />
      {SERVICES.map((s) => (
        <Node key={s.name} x={s.x} y={368} w={CARD_W} h={120} title={s.name} port={s.port} lines={s.lines} mono={s.store} />
      ))}
      <Node
        x={48}
        y={560}
        w={1056}
        h={64}
        variant="events"
        title="Apache Kafka · event bus"
        lines={['bid-placed · auction-ended · auction-extended · deposit-refunded · payment-event · audit-events']}
      />

      {/* captions */}
      <Label x={588} y={160} text="REST + STOMP/WebSocket" />
      <Label x={342} y={224} anchor="middle" text="lb:// routing" />
      <Label x={342} y={264} anchor="middle" text="services register" />
      <Label x={208} y={416} anchor="middle" size={12} fill="var(--color-info-default)" text="Feign" />
      <Label x={576} y={416} anchor="middle" size={12} fill="var(--color-info-default)" text="Feign" />
      <Label x={760} y={416} anchor="middle" size={12} fill="var(--color-info-default)" text="Feign" />
      <Label x={760} y={208} text="The gateway is the only component that reads the JWT." size={13} />
      <Label x={760} y={228} text="It adds X-User-Id and X-User-Roles, and each service" size={13} />
      <Label x={760} y={248} text="trusts those headers." size={13} />
    </svg>
  )
}
