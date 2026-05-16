import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export default function AdminSettingsPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>System Settings</CardTitle>
        <CardDescription>Platform configuration controls will appear here.</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex min-h-64 items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
          No system settings yet.
        </div>
      </CardContent>
    </Card>
  )
}
