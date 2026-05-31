import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export default function AdminAuctionsPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Auction Monitoring</CardTitle>
        <CardDescription>Admin auction review, moderation, and monitoring tools will appear here.</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex min-h-64 items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
          No auction monitoring tools yet.
        </div>
      </CardContent>
    </Card>
  )
}
