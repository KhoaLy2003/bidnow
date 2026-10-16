import { Arrow, ArrowMarkers, Label, Node } from './diagram-parts'

const P = 'aws'

const SUPPORT = [
  { x: 48,  name: 'Route 53',   what: 'DNS' },
  { x: 236, name: 'ACM',        what: 'certificates' },
  { x: 424, name: 'AWS WAF',    what: 'filtering' },
  { x: 612, name: 'ECR',        what: 'images' },
  { x: 800, name: 'CloudWatch', what: 'logs' },
  { x: 988, name: 'IAM',        what: 'roles' },
] as const

const MUTED = 'var(--color-text-secondary)'

/**
 * The target AWS deployment: CloudFront and S3 at the edge, then one ALB feeding two Auto Scaling Groups
 * (Next.js and the Spring Boot stack), then the managed data stores. Meant to sit on a `dark` surface.
 */
export function AwsArchitectureDiagram() {
  return (
    <svg
      viewBox="0 0 1200 656"
      role="img"
      aria-labelledby="aws-title aws-desc"
      className="block h-auto w-full min-w-[1000px] font-sans"
    >
      <title id="aws-title">BidNow on AWS: three-tier architecture</title>
      <desc id="aws-desc">
        Users reach Amazon CloudFront, which serves static and media files from S3 and forwards dynamic requests to
        an Application Load Balancer in the VPC. The load balancer sends page requests to an Auto Scaling Group of
        Next.js EC2 instances and API requests to an Auto Scaling Group of EC2 instances running the gateway and the
        Spring Boot services. Both groups span two Availability Zones in private subnets. The application tier uses
        RDS for PostgreSQL, ElastiCache for Redis and Amazon MSK in the data tier.
      </desc>
      <ArrowMarkers prefix={P} />

      {/* zones */}
      <rect x={392} y={48} width={760} height={476} rx={12} fill="none" strokeWidth={1} strokeDasharray="5 4" style={{ stroke: 'var(--color-border-strong)' }} />
      <Node x={408} y={88} w={184} h={420} variant="band" />
      <Node x={640} y={88} w={240} h={420} variant="band" />
      <Node x={928} y={88} w={208} h={420} variant="band" />
      <Node x={424} y={292} w={152} h={172} variant="zone" />
      <Node x={656} y={132} w={208} h={340} variant="zone" />

      {/* traffic */}
      <Arrow prefix={P} tone="traffic" d="M160 196 H208" />
      <Arrow prefix={P} tone="traffic" d="M360 196 H424" />
      <Arrow prefix={P} tone="traffic" d="M284 244 V292" />
      <Arrow prefix={P} tone="traffic" d="M500 244 V292" />
      <Arrow prefix={P} tone="traffic" d="M576 196 H656" />
      <Arrow prefix={P} tone="traffic" d="M576 378 H656" />

      {/* data access */}
      <Arrow prefix={P} tone="data" both d="M864 176 H944" />
      <Arrow prefix={P} tone="data" both d="M864 308 H944" />
      <Arrow prefix={P} tone="data" both d="M864 440 H944" />

      {/* zone titles */}
      <Label x={408} y={68} text="VPC · ALB in public subnets, instances and data in private subnets" size={12} mono fill={MUTED} />
      <Label x={420} y={112} text="TIER 1 · PRESENTATION" size={11} mono fill={MUTED} />
      <Label x={652} y={112} text="TIER 2 · APPLICATION" size={11} mono fill={MUTED} />
      <Label x={940} y={112} text="TIER 3 · DATA" size={11} mono fill={MUTED} />
      <Label x={436} y={312} text="Auto Scaling Group" size={12} fill="var(--color-text-brand)" />
      <Label x={668} y={152} text="Auto Scaling Group" size={12} fill="var(--color-text-brand)" />

      {/* edge */}
      <Node x={48}  y={148} w={112} h={96} title="Users" lines={['Web browser']} />
      <Node x={208} y={148} w={152} h={96} variant="accent" title="Amazon CloudFront" lines={['CDN · TLS · WAF']} />
      <Node x={208} y={292} w={152} h={96} title="Amazon S3" lines={['Media uploads', 'and static assets']} />

      {/* tier 1 */}
      <Node x={424} y={148} w={152} h={96} variant="accent" title={['Application', 'Load Balancer']} lines={['/ to web', '/api, /ws to app']} />
      <Node x={436} y={324} w={128} h={56} title="EC2 · AZ a" titleSize={14} lines={['Next.js server']} />
      <Node x={436} y={392} w={128} h={56} title="EC2 · AZ b" titleSize={14} lines={['Next.js server']} />

      {/* tier 2 */}
      <Node x={672} y={168} w={176} h={136} title="EC2 · AZ a" lines={['Gateway, Eureka and the', 'six domain services as', 'Docker containers']} />
      <Node x={672} y={316} w={176} h={136} title="EC2 · AZ b" lines={['Gateway, Eureka and the', 'six domain services as', 'Docker containers']} />

      {/* tier 3 */}
      <Node x={944} y={132} w={176} h={88} variant="data" title="RDS for PostgreSQL" lines={['Multi-AZ · one database', 'per service']} />
      <Node x={944} y={264} w={176} h={88} variant="data" title="ElastiCache for Redis" lines={['Cache and bid', 'leaderboards']} />
      <Node x={944} y={396} w={176} h={88} variant="data" title="Amazon MSK" lines={['Managed Kafka event bus']} />

      {/* supporting services */}
      <Label x={48} y={560} text="SUPPORTING SERVICES" size={12} mono fill={MUTED} />
      {SUPPORT.map((s) => (
        <Node key={s.name} x={s.x} y={572} w={164} h={44} variant="chip" lines={[`${s.name} · ${s.what}`]} />
      ))}

      {/* captions */}
      <Label x={184} y={184} anchor="middle" text="HTTPS" />
      <Label x={296} y={272} text="origin" />
    </svg>
  )
}
