"use client";

import { User, Shield } from "lucide-react";
import { UserAvatar } from "@/components/shared/UserAvatar";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { useProfile } from "@/hooks/useProfile";
import { useAuthStore } from "@/store/authStore";

export default function ProfilePage() {
  const { profile, isLoading, error } = useProfile();
  const authUser = useAuthStore((s) => s.user);

  if (isLoading) {
    return <ProfileSkeleton />;
  }

  if (error) {
    return (
      <div className="flex flex-col gap-8 max-w-lg">
        <PageHeader />
        <div className="rounded-xl border bg-destructive/10 p-6 text-destructive text-sm">
          {error}
        </div>
      </div>
    );
  }

  // Display name falls back to email from auth store if profile has no displayName yet
  const displayName = profile?.displayName ?? authUser?.email ?? "—";
  const email = authUser?.email ?? "—";
  const isVerified = authUser?.isEmailVerified ?? false;

  return (
    <div className="flex flex-col gap-8 max-w-lg">
      <PageHeader />

      <div className="rounded-xl border bg-card p-6 flex flex-col gap-6">
        {/* Avatar + name */}
        <div className="flex items-center gap-4">
          <UserAvatar
            name={displayName}
            avatarUrl={profile?.avatarUrl ?? undefined}
            size="xl"
          />
          <div>
            <p className="font-semibold text-base flex items-center gap-1.5">
              {displayName}
              {isVerified && (
                <Shield className="size-4 text-[var(--color-text-brand)]" />
              )}
            </p>
            <p className="text-sm text-muted-foreground">{email}</p>
            {profile?.roles && profile.roles.length > 0 && (
              <p className="text-xs text-muted-foreground mt-0.5">
                {profile.roles.join(", ")}
              </p>
            )}
          </div>
        </div>

        <Separator />

        {/* Bio */}
        {profile?.bio && (
          <>
            <p className="text-sm text-muted-foreground">{profile.bio}</p>
            <Separator />
          </>
        )}

        {/* Location */}
        {(profile?.city || profile?.country) && (
          <>
            <p className="text-sm text-muted-foreground">
              {[profile.city, profile.country].filter(Boolean).join(", ")}
            </p>
            <Separator />
          </>
        )}

        {/* Preferences */}
        {(profile?.language || profile?.timezone || profile?.currency) && (
          <>
            <div className="grid grid-cols-3 gap-4 text-center">
              {profile.language && (
                <PreferenceItem label="Language" value={profile.language} />
              )}
              {profile.timezone && (
                <PreferenceItem label="Timezone" value={profile.timezone} />
              )}
              {profile.currency && (
                <PreferenceItem label="Currency" value={profile.currency} />
              )}
            </div>
            <Separator />
          </>
        )}

        <Button variant="outline" className="w-full">
          Edit Profile
        </Button>
      </div>
    </div>
  );
}

function PageHeader() {
  return (
    <div className="flex items-center gap-2">
      <User className="size-5 text-[var(--color-text-brand)]" />
      <h1 className="font-display font-bold text-[length:var(--font-size-2xl)]">
        Profile
      </h1>
    </div>
  );
}

function PreferenceItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="font-mono font-bold text-[length:var(--font-size-xl)]">
        {value}
      </span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="flex flex-col gap-8 max-w-lg">
      <div className="flex items-center gap-2">
        <Skeleton className="size-5 rounded" />
        <Skeleton className="h-7 w-24 rounded" />
      </div>
      <div className="rounded-xl border bg-card p-6 flex flex-col gap-6">
        <div className="flex items-center gap-4">
          <Skeleton className="size-16 rounded-full" />
          <div className="flex flex-col gap-2">
            <Skeleton className="h-5 w-32 rounded" />
            <Skeleton className="h-4 w-48 rounded" />
          </div>
        </div>
        <Skeleton className="h-px w-full" />
        <Skeleton className="h-9 w-full rounded" />
      </div>
    </div>
  );
}
