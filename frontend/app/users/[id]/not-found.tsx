import type { Metadata } from 'next'
import { ErrorPage } from '@/components/static/ErrorPage'

export const metadata: Metadata = { title: 'Profile not found' }

export default function ProfileNotFound() {
  return (
    <ErrorPage
      code="404"
      accentColor="var(--color-brand-600)"
      statusLabel="HTTP · 404 Not Found"
      heading="We couldn't find that member."
      body="This profile doesn't exist or the link is out of date."
      primaryAction={{ label: '← Back to Browse', href: '/auctions' }}
      secondaryAction={{ label: 'Go to home', href: '/' }}
    />
  )
}
