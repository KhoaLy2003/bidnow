import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { CalendarDays, Lock, MapPin } from 'lucide-react'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { UserAvatar } from '@/components/shared/UserAvatar'
import { userService } from '@/services/user.service'
import { formatMemberSince } from '@/lib/format'

interface PublicProfilePageProps {
  params: Promise<{ id: string }>
}

export async function generateMetadata({ params }: PublicProfilePageProps): Promise<Metadata> {
  const { id } = await params
  const profile = await userService.getPublicProfile(id).catch(() => null)
  return { title: profile?.name ?? 'Member profile' }
}

export default async function PublicProfilePage({ params }: PublicProfilePageProps) {
  const { id } = await params
  const profile = await userService.getPublicProfile(id)
  if (!profile) notFound()

  return (
    <>
      <Header />
      <main id="main-content" className="flex-1 mx-auto w-full max-w-[var(--container-auction-detail)] px-4 py-8 pb-24 md:px-6 md:pb-14">
        <div className="flex flex-wrap items-start gap-6">
          <section
            aria-label="Profile summary"
            className="flex min-w-0 flex-[1_1_300px] flex-col gap-5 rounded-2xl border border-[var(--color-border-default)] p-7 md:max-w-[360px]"
          >
            <UserAvatar name={profile.name} avatarUrl={profile.avatarUrl} size="xl" />

            <h1 className="font-display text-[1.75rem] font-semibold leading-[1.15] tracking-tight break-words">
              {profile.name}
            </h1>

            <div className="h-px bg-[var(--color-border-default)]" />

            <dl className="flex flex-col gap-3.5">
              {profile.location && (
                <ProfileFact icon={<MapPin className="size-[18px]" aria-hidden />} label="Location" value={profile.location} />
              )}
              <ProfileFact
                icon={<CalendarDays className="size-[18px]" aria-hidden />}
                label="Member since"
                value={formatMemberSince(profile.memberSince)}
              />
            </dl>
          </section>

          <div className="flex min-w-0 flex-[999_1_480px] flex-col gap-6">
            <section aria-labelledby="about-heading" className="flex flex-col gap-3.5 rounded-2xl border border-[var(--color-border-default)] p-7">
              <h2 id="about-heading" className="font-display text-[1.375rem] font-semibold tracking-tight">About</h2>
              {profile.bio ? (
                <p className="whitespace-pre-line text-base leading-[1.65] text-[var(--color-text-secondary)]">{profile.bio}</p>
              ) : (
                <p className="text-[15px] leading-relaxed text-muted-foreground">This member hasn&apos;t added a bio yet.</p>
              )}
            </section>

            <aside className="flex items-start gap-3 rounded-2xl bg-muted px-6 py-5">
              <Lock className="mt-0.5 size-[18px] shrink-0 text-muted-foreground" aria-hidden />
              <p className="text-sm leading-relaxed text-[var(--color-text-secondary)]">
                Members choose what to share. Only the display name, photo, bio, city and country, and join date appear
                here. Email, phone and street address stay private.
              </p>
            </aside>
          </div>
        </div>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}

function ProfileFact({ icon, label, value }: { readonly icon: React.ReactNode; readonly label: string; readonly value: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="pt-px text-muted-foreground">{icon}</span>
      <div className="min-w-0">
        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
        <dd className="mt-0.5 text-[15px] font-medium break-words">{value}</dd>
      </div>
    </div>
  )
}
