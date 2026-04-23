import type { Metadata } from 'next'
import { User, Shield, Star } from 'lucide-react'
import { UserAvatar } from '@/components/shared/UserAvatar'
import { Button }     from '@/components/ui/button'
import { Separator }  from '@/components/ui/separator'

export const metadata: Metadata = { title: 'Profile' }

export default function ProfilePage() {
  return (
    <div className="flex flex-col gap-8 max-w-lg">
      <div className="flex items-center gap-2">
        <User className="size-5 text-[var(--color-text-brand)]" />
        <h1 className="font-display font-bold text-[length:var(--font-size-2xl)]">Profile</h1>
      </div>

      <div className="rounded-xl border bg-card p-6 flex flex-col gap-6">
        {/* Avatar + name */}
        <div className="flex items-center gap-4">
          <UserAvatar name="Hiep Nguyen" size="xl" />
          <div>
            <p className="font-semibold text-base flex items-center gap-1.5">
              Hiep Nguyen
              <Shield className="size-4 text-[var(--color-text-brand)]" />
            </p>
            <p className="text-sm text-muted-foreground">hiepskyc@gmail.com</p>
          </div>
        </div>

        <Separator />

        {/* Stats */}
        <div className="grid grid-cols-3 gap-4 text-center">
          {[
            { label: 'Total Bids', value: '48' },
            { label: 'Auctions Won', value: '12' },
            { label: 'Rating', value: '4.9 ★' },
          ].map(({ label, value }) => (
            <div key={label} className="flex flex-col gap-0.5">
              <span className="font-mono font-bold text-[length:var(--font-size-xl)]">{value}</span>
              <span className="text-xs text-muted-foreground">{label}</span>
            </div>
          ))}
        </div>

        <Separator />

        <Button variant="outline" className="w-full">Edit Profile</Button>
      </div>
    </div>
  )
}
