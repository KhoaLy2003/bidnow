/** Static content for the About page (`/about`): architecture facts, tech stack and the team. */

export interface Principle {
  n:     string
  title: string
  body:  string
}

export const PRINCIPLES: readonly Principle[] = [
  {
    n: '01',
    title: 'A database per service',
    body: 'Each service owns its PostgreSQL database, versioned with Liquibase. Nothing queries across them, so a service can change its schema without asking anyone.',
  },
  {
    n: '02',
    title: 'One door in',
    body: 'The API gateway is the only component that reads the JWT. It forwards X-User-Id and X-User-Roles, and every downstream service trusts those headers.',
  },
  {
    n: '03',
    title: 'Events for what can wait',
    body: 'Bids, auction lifecycle changes, deposits and audit entries travel as Kafka events, so a slow consumer can lag without holding up the bidder.',
  },
  {
    n: '04',
    title: 'Live by default',
    body: 'Auction pages subscribe to a STOMP topic, so prices, countdowns and anti-sniping extensions are pushed to the browser instead of polled.',
  },
]

export interface Note {
  title: string
  body:  string
}

export const SERVICE_NOTES: readonly Note[] = [
  {
    title: 'Request path',
    body: 'Browser to gateway to service. The gateway validates the token, adds the identity headers and resolves lb:// routes through Eureka. A shared common module supplies the response wrapper, error handling and header filter.',
  },
  {
    title: 'Synchronous calls',
    body: 'OpenFeign is used when a reply is needed inline: identity creates a profile in user, and bidding reads auctions and talks to wallet. Internal endpoints live under /internal and are never routed by the gateway.',
  },
  {
    title: 'Events and audit',
    body: 'Kafka carries bid, auction, deposit and payment events. An @Audit aspect publishes before and after snapshots to the audit-events topic, which media-service persists as the audit log.',
  },
]

export const AWS_NOTES: readonly Note[] = [
  {
    title: 'Edge',
    body: 'CloudFront terminates TLS with an ACM certificate, filters with WAF and caches static files. Uploaded images already flow through a CDN base URL, backed by S3.',
  },
  {
    title: 'Load balancing',
    body: 'One internet-facing ALB in the public subnets. Path rules send / to the Next.js target group and /api and /ws to the gateway. Health checks drive instance replacement.',
  },
  {
    title: 'Compute',
    body: 'EC2 instances in private subnets run the Docker images built by CI. To start, each application instance runs the full stack; services can move to their own groups as load demands.',
  },
  {
    title: 'Data',
    body: 'RDS PostgreSQL (Multi-AZ), ElastiCache for Redis and Amazon MSK replace the local containers. Instances use IAM roles, so no access keys are stored.',
  },
]

export interface StackItem {
  name: string
  role: string
}

export interface StackGroup {
  title: string
  items: readonly StackItem[]
}

export const STACK: readonly StackGroup[] = [
  {
    title: 'Frontend',
    items: [
      { name: 'Next.js 16',            role: 'App Router, server rendering' },
      { name: 'React 19',              role: 'UI' },
      { name: 'TypeScript',            role: 'Typed API payloads' },
      { name: 'Tailwind CSS 4',        role: 'Styling, design tokens' },
      { name: 'shadcn/ui, Base UI',    role: 'Components' },
      { name: 'Zustand',               role: 'Client state' },
      { name: 'STOMP over WebSocket',  role: 'Live bids' },
      { name: 'Three.js, Motion',      role: 'Hero watch, animation' },
      { name: 'Vitest',                role: 'Unit tests' },
    ],
  },
  {
    title: 'Backend',
    items: [
      { name: 'Java 17',               role: 'Language' },
      { name: 'Spring Boot 3.2',       role: 'Service runtime' },
      { name: 'Spring Cloud Gateway',  role: 'Routing, JWT check' },
      { name: 'Eureka, OpenFeign',     role: 'Discovery, sync calls' },
      { name: 'Spring Security, JJWT', role: 'Auth' },
      { name: 'Liquibase',             role: 'Schema migrations' },
      { name: 'JobRunr',               role: 'Scheduled auction jobs' },
      { name: 'MapStruct, Lombok',     role: 'Mapping, boilerplate' },
      { name: 'springdoc OpenAPI',     role: 'Swagger at the gateway' },
    ],
  },
  {
    title: 'Data and messaging',
    items: [
      { name: 'PostgreSQL 15+',        role: 'One database per service' },
      { name: 'Apache Kafka 3.7',      role: 'Event bus' },
      { name: 'Redis 7',               role: 'Cache, bid leaderboards' },
      { name: 'STOMP broker',          role: 'Kafka events to browsers' },
      { name: 'Amazon S3, CloudFront', role: 'Media uploads, CDN' },
    ],
  },
  {
    title: 'Quality and delivery',
    items: [
      { name: 'JUnit, Mockito',        role: 'Unit tests' },
      { name: 'Cucumber',              role: 'BDD scenarios' },
      { name: 'JaCoCo',                role: 'Coverage' },
      { name: 'GitHub Actions',        role: 'CI, security, image builds' },
      { name: 'Dependabot',            role: 'Dependency updates' },
      { name: 'Docker, Maven',         role: 'Images, multi-module build' },
    ],
  },
]

export interface Developer {
  name:   string
  handle: string
}

export const DEVELOPERS: readonly Developer[] = [
  { name: 'Hiep Nguyen', handle: 'hiepnguyen06' },
  { name: 'Khoa Ly',     handle: 'KhoaLy2003' },
]

export function githubUrl(handle: string): string {
  return `https://github.com/${handle}`
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0].toUpperCase())
    .slice(0, 2)
    .join('')
}
