import type { Metadata } from 'next'
import { ErrorPage } from '@/components/static/ErrorPage'

export const metadata: Metadata = { title: '404 · Not Found' }

export default function NotFound() {
  return (
    <ErrorPage
      code="404"
      accentColor="var(--color-brand-600)"
      statusLabel="HTTP · 404 Not Found"
      heading="We couldn't find that auction."
      body="The listing may have ended, been removed, or the link may be incorrect."
      primaryAction={{ label: '← Back to Browse', href: '/auctions' }}
      secondaryAction={{ label: 'Go to home', href: '/' }}
    />
  )
}
