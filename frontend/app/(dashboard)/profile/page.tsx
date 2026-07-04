"use client";

import { User, Shield, MapPin, Phone, Mail, Clock, Globe, DollarSign, Edit2, Check, X, Camera, Loader2 } from "lucide-react";
import { UserAvatar } from "@/components/shared/UserAvatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { useProfile } from "@/hooks/useProfile";
import { useAuthStore } from "@/store/authStore";
import { useState, useEffect, useRef } from "react";
import { toast } from "sonner";
import { getErrorMessage } from "@/lib/utils";
import { mediaService } from "@/services/media.service";

function profileToForm(p: { displayName?: string | null; phoneNumber?: string | null; address?: string | null; city?: string | null; country?: string | null; postalCode?: string | null; bio?: string | null } | null) {
  return {
    displayName: p?.displayName || "",
    phoneNumber: p?.phoneNumber || "",
    address: p?.address || "",
    city: p?.city || "",
    country: p?.country || "",
    postalCode: p?.postalCode || "",
    bio: p?.bio || "",
  };
}

export default function ProfilePage() {
  const { profile, isLoading, error, updateProfile } = useProfile();
  const authUser = useAuthStore((s) => s.user);

  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [formData, setFormData] = useState({
    displayName: "",
    phoneNumber: "",
    address: "",
    city: "",
    country: "",
    postalCode: "",
    bio: "",
  });

  useEffect(() => {
    if (profile) setFormData(profileToForm(profile));
  }, [profile]);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await updateProfile(formData);
      toast.success("Profile updated successfully!");
      setIsEditing(false);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to update profile."));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    if (profile) setFormData(profileToForm(profile));
    setIsEditing(false);
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 10 * 1024 * 1024) {
      toast.error("File size must be less than 10MB");
      return;
    }

    setIsUploadingAvatar(true);
    try {
      // 1. Upload to media-service, tagging with the user's own id as entityId
      const userId = authUser?.id;
      const uploadRes = await mediaService.uploadFile(
        file,
        "USER_AVATAR",
        userId,
      );

      const publicUrl = uploadRes.publicUrl;

      // 2. Persist the new avatarUrl directly to the user-profile in one call
      await updateProfile({ avatarUrl: publicUrl });
      toast.success("Avatar updated successfully!");
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Failed to upload avatar"));
    } finally {
      setIsUploadingAvatar(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  if (isLoading) {
    return <ProfileSkeleton />;
  }

  if (error) {
    return (
      <div className="flex flex-col gap-8 max-w-5xl mx-auto w-full">
        <PageHeader />
        <Card className="border-destructive/50 bg-destructive/10">
          <CardContent className="pt-6 text-destructive text-sm font-medium">
            {error}
          </CardContent>
        </Card>
      </div>
    );
  }

  const displayName = profile?.displayName ?? authUser?.email ?? "—";
  const email = authUser?.email ?? "—";
  const isVerified = authUser?.isEmailVerified ?? false;

  return (
    <div className="flex flex-col gap-8 max-w-5xl mx-auto w-full pb-10">
      <div className="flex items-center justify-between">
        <PageHeader />
        {!isEditing ? (
          <Button onClick={() => setIsEditing(true)} variant="outline" className="gap-2">
            <Edit2 className="size-4" /> Edit Profile
          </Button>
        ) : (
          <div className="flex items-center gap-2">
            <Button onClick={handleCancel} variant="ghost" disabled={isSaving || isUploadingAvatar}>
              <X className="size-4 mr-2" /> Cancel
            </Button>
            <Button onClick={handleSave} disabled={isSaving || isUploadingAvatar}>
              {isSaving ? "Saving..." : <><Check className="size-4 mr-2" /> Save Changes</>}
            </Button>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Main Identity Column */}
        <div className="md:col-span-1 space-y-6">
          <Card>
            <CardContent className="pt-6 flex flex-col items-center text-center gap-4">
              <div className="relative group cursor-pointer" onClick={() => fileInputRef.current?.click()}>
                <UserAvatar
                  name={displayName}
                  avatarUrl={profile?.avatarUrl ?? undefined}
                  size="xl"
                  className={`size-32 items-center justify-center transition-opacity duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] ${isUploadingAvatar ? 'opacity-50' : 'group-hover:opacity-75'}`}
                />
                <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity">
                  {isUploadingAvatar ? (
                    <Loader2 className="size-8 text-white animate-spin" />
                  ) : (
                    <Camera className="size-8 text-white" />
                  )}
                </div>
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  className="hidden" 
                  accept="image/jpeg, image/png, image/webp" 
                  onChange={handleAvatarChange}
                />
              </div>
              <div className="space-y-1 w-full">
                {isEditing ? (
                  <div className="space-y-2 text-left">
                    <Label htmlFor="displayName">Display Name</Label>
                    <Input
                      id="displayName"
                      value={formData.displayName}
                      onChange={(e) => setFormData(p => ({ ...p, displayName: e.target.value }))}
                      placeholder="Your name"
                    />
                  </div>
                ) : (
                  <>
                    <h2 className="font-medium text-xl flex items-center justify-center gap-2">
                      {displayName}
                      {isVerified && <Shield className="size-5 text-[var(--color-text-brand)]" />}
                    </h2>
                    <p className="text-sm text-muted-foreground break-all">{email}</p>
                  </>
                )}

                {!isEditing && profile?.roles && profile.roles.length > 0 && (
                  <div className="flex flex-wrap justify-center gap-2 mt-4">
                    {profile.roles.map(role => (
                      <span key={role} className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-xs font-medium">
                        {role}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <User className="size-4" /> About Me
              </CardTitle>
            </CardHeader>
            <CardContent>
              {isEditing ? (
                <div className="space-y-2">
                  <Textarea
                    className="min-h-[120px] resize-none"
                    value={formData.bio}
                    onChange={(e) => setFormData(p => ({ ...p, bio: e.target.value }))}
                    placeholder="Tell us about yourself..."
                  />
                </div>
              ) : (
                <p className="text-sm leading-relaxed whitespace-pre-wrap">
                  {profile?.bio || "No bio provided yet."}
                </p>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Details Column */}
        <div className="md:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Contact & Location</CardTitle>
              <CardDescription>Your personal contact information and address details.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {isEditing ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="phoneNumber">Phone Number</Label>
                    <Input
                      id="phoneNumber"
                      value={formData.phoneNumber}
                      onChange={(e) => setFormData(p => ({ ...p, phoneNumber: e.target.value }))}
                      placeholder="+1 (555) 000-0000"
                    />
                  </div>
                  <div className="space-y-2 sm:col-span-2">
                    <Label htmlFor="address">Street Address</Label>
                    <Input
                      id="address"
                      value={formData.address}
                      onChange={(e) => setFormData(p => ({ ...p, address: e.target.value }))}
                      placeholder="123 Main St"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="city">City</Label>
                    <Input
                      id="city"
                      value={formData.city}
                      onChange={(e) => setFormData(p => ({ ...p, city: e.target.value }))}
                      placeholder="New York"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="country">Country</Label>
                    <Input
                      id="country"
                      value={formData.country}
                      onChange={(e) => setFormData(p => ({ ...p, country: e.target.value }))}
                      placeholder="United States"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="postalCode">Postal Code</Label>
                    <Input
                      id="postalCode"
                      value={formData.postalCode}
                      onChange={(e) => setFormData(p => ({ ...p, postalCode: e.target.value }))}
                      placeholder="10001"
                    />
                  </div>
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-6 gap-x-4">
                  <InfoItem icon={<Phone className="size-4" />} label="Phone" value={profile?.phoneNumber} />
                  <InfoItem icon={<Mail className="size-4" />} label="Email" value={email} />
                  <InfoItem icon={<MapPin className="size-4" />} label="Address" value={profile?.address} className="sm:col-span-2" />
                  <InfoItem icon={<Globe className="size-4" />} label="Location" value={[profile?.city, profile?.country].filter(Boolean).join(", ")} />
                  <InfoItem icon={<MapPin className="size-4" />} label="Postal Code" value={profile?.postalCode} />
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Preferences</CardTitle>
              <CardDescription>Your regional and display preferences.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
                <PreferenceItem icon={<Globe className="size-5" />} label="Language" value={profile?.language || "English (Default)"} />
                <PreferenceItem icon={<Clock className="size-5" />} label="Timezone" value={profile?.timezone || "UTC (Default)"} />
                <PreferenceItem icon={<DollarSign className="size-5" />} label="Currency" value={profile?.currency || "USD (Default)"} />
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function PageHeader() {
  return (
    <div className="flex items-center gap-2">
      <User className="size-6 text-[var(--color-text-brand)]" />
      <h1 className="font-display font-medium text-[length:var(--font-size-3xl)]">
        My Profile
      </h1>
    </div>
  );
}

function InfoItem({ icon, label, value, className }: { icon: React.ReactNode, label: string, value?: string | null, className?: string }) {
  return (
    <div className={`flex items-start gap-3 ${className || ""}`}>
      <div className="p-2 rounded-lg bg-muted text-muted-foreground">
        {icon}
      </div>
      <div>
        <p className="text-xs font-medium text-muted-foreground uppercase">{label}</p>
        <p className="text-sm font-medium mt-0.5">{value || "—"}</p>
      </div>
    </div>
  );
}

function PreferenceItem({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex flex-col gap-3 p-4 rounded-xl border bg-card/50">
      <div className="flex items-center gap-2 text-muted-foreground">
        {icon}
        <span className="text-xs font-medium uppercase">{label}</span>
      </div>
      <span className="font-medium text-base">
        {value}
      </span>
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="flex flex-col gap-8 max-w-5xl mx-auto w-full pb-10">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Skeleton className="size-6 rounded" />
          <Skeleton className="h-8 w-32 rounded" />
        </div>
        <Skeleton className="h-10 w-32 rounded" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-1 space-y-6">
          <Card>
            <CardContent className="pt-6 flex flex-col items-center gap-4">
              <Skeleton className="size-32 rounded-full" />
              <Skeleton className="h-6 w-3/4 rounded" />
              <Skeleton className="h-4 w-full rounded" />
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <Skeleton className="h-24 w-full rounded" />
            </CardContent>
          </Card>
        </div>

        <div className="md:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <Skeleton className="h-6 w-1/3 rounded mb-2" />
              <Skeleton className="h-4 w-1/2 rounded" />
            </CardHeader>
            <CardContent className="space-y-6">
              <Skeleton className="h-10 w-full rounded" />
              <Skeleton className="h-10 w-full rounded" />
              <Skeleton className="h-10 w-full rounded" />
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
