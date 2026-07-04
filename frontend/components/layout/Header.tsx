"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { User, LogOut, Settings, ListOrdered, Package } from "lucide-react";
import { BidNowGavelMark } from "@/components/shared/BidNowGavelMark";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuLabel,
  DropdownMenuGroup,
} from "@/components/ui/dropdown-menu";
import { SearchBar } from "@/components/shared/SearchBar";
import { UserAvatar } from "@/components/shared/UserAvatar";
import { ThemeToggle } from "@/components/shared/ThemeToggle";
import { WalletBadge } from "@/components/wallet/WalletBadge";
import { NotificationBell } from "@/components/notification/NotificationBell";
import { cn } from "@/lib/utils";

import { useAuthStore } from "@/store/authStore";
import { authService } from "@/services/auth.service";
import { useProfile } from "@/hooks/useProfile";
import { useRouter } from "next/navigation";

const NAV_LINKS = [
  { label: "Browse", href: "/auctions" },
  { label: "My Bids", href: "/my-bids" },
  { label: "Sell", href: "/sell" },
] as const;

export function Header() {
  const router = useRouter();
  const { user, isAuthenticated, logout, refreshToken } = useAuthStore();
  const { profile } = useProfile();
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const handleLogout = async () => {
    if (refreshToken) {
      await authService.logout(refreshToken, useAuthStore.getState().accessToken);
    }
    logout();
    router.push("/login");
  };

  return (
    <>
    <header
      className={cn(
        "sticky top-0 z-[var(--z-index-sticky)] h-16 bg-background border-b border-border transition-[backdrop-filter] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]",
        scrolled && "backdrop-blur-sm",
      )}
    >
      <div className="mx-auto flex h-full max-w-[var(--container-xl)] items-center gap-4 px-4">
        {/* Wordmark */}
        <Link href="/" className="flex shrink-0 items-center gap-2">
          <BidNowGavelMark size={28} />
          <span className="hidden font-medium sm:inline-block">
            Bid<span className="text-[var(--color-text-brand)] font-medium">Now</span>
          </span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden md:flex items-center gap-1">
          {NAV_LINKS.map(({ label, href }) => (
            <Button
              key={href}
              variant="ghost"
              size="sm"
              render={<Link href={href} />}
              nativeButton={false}
            >
              {label}
            </Button>
          ))}
        </nav>

        {/* Search */}
        <SearchBar className="hidden sm:flex flex-1 max-w-[400px] mx-auto" />

        {/* Right controls */}
        <div className="ml-auto flex items-center gap-1 shrink-0">
          <ThemeToggle />

          {isAuthenticated ? (
            <>
              {user?.role !== 'ADMIN' && (
                <>
                  <WalletBadge />
                  <NotificationBell />
                </>
              )}

              {/* User menu */}
              {user?.role !== 'ADMIN' ? (
                <DropdownMenu>
                  <DropdownMenuTrigger
                    render={
                      <Button
                        variant="ghost"
                        size="icon"
                        className="rounded-full"
                      />
                    }
                  >
                    <UserAvatar
                      name={profile?.displayName || user?.email || "User"}
                      avatarUrl={profile?.avatarUrl ?? undefined}
                      size="sm"
                    />
                    <span className="sr-only">Account menu</span>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent
                    side="bottom"
                    align="end"
                    sideOffset={8}
                    className="w-64 p-2 border-border/50 backdrop-blur-md"
                  >
                    <DropdownMenuGroup>
                      <DropdownMenuLabel className="p-2 font-normal">
                        <div className="flex flex-col gap-3">
                          <div className="flex items-center gap-3">
                            <UserAvatar
                              name={profile?.displayName || user?.email || "User"}
                              avatarUrl={profile?.avatarUrl ?? undefined}
                              size="lg"
                            />
                            <div className="flex flex-col min-w-0">
                              <p className="font-medium text-sm truncate">
                                {profile?.displayName || user?.email?.split("@")[0]}
                              </p>
                              <p className="text-xs text-muted-foreground truncate">
                                {user?.email}
                              </p>
                            </div>
                          </div>
                          <div className="bg-accent/50 rounded-md p-2 flex items-center justify-between">
                            <span className="text-[10px] font-medium uppercase text-muted-foreground">
                              Status
                            </span>
                            <span className="text-[10px] font-medium uppercase text-[var(--color-success-default)]">
                              Verified
                            </span>
                          </div>
                        </div>
                      </DropdownMenuLabel>
                    </DropdownMenuGroup>

                    <DropdownMenuSeparator className="my-2" />

                    <DropdownMenuGroup>
                      <DropdownMenuItem
                        render={
                          <Link
                            href="/profile"
                            className="flex items-center gap-2"
                          />
                        }
                        className="py-2.5"
                      >
                        <User className="size-4" /> Profile
                      </DropdownMenuItem>

                      <DropdownMenuItem
                        render={
                          <Link
                            href="/my-bids"
                            className="flex items-center gap-2"
                          />
                        }
                        className="py-2.5"
                      >
                        <ListOrdered className="size-4" /> My Bids
                      </DropdownMenuItem>

                      <DropdownMenuItem
                        render={
                          <Link
                            href="/seller/auctions"
                            className="flex items-center gap-2"
                          />
                        }
                        className="py-2.5"
                      >
                        <Package className="size-4" /> My Auctions
                      </DropdownMenuItem>

                      <DropdownMenuItem
                        render={
                          <Link
                            href="/settings"
                            className="flex items-center gap-2"
                          />
                        }
                        className="py-2.5"
                      >
                        <Settings className="size-4" /> Settings
                      </DropdownMenuItem>
                    </DropdownMenuGroup>

                    <DropdownMenuSeparator className="my-2" />

                    <DropdownMenuItem
                      variant="destructive"
                      className="gap-2 cursor-pointer py-2.5"
                      onClick={handleLogout}
                    >
                      <LogOut className="size-4" /> Sign out
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <Button 
                  variant="ghost" 
                  size="sm" 
                  onClick={handleLogout}
                  className="gap-2 text-muted-foreground hover:text-foreground"
                >
                  <LogOut className="size-4" />
                  Sign out
                </Button>
              )}

              {/* Sell CTA — desktop */}
              {user?.role !== 'ADMIN' && (
                <Button
                  variant="brand"
                  size="sm"
                  className="hidden md:inline-flex ml-1"
                  render={<Link href="/sell" />}
                  nativeButton={false}
                >
                  Sell
                </Button>
              )}
            </>
          ) : (
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                render={<Link href="/login" />}
                nativeButton={false}
              >
                Sign in
              </Button>
              <Button
                variant="brand"
                size="sm"
                render={<Link href="/register" />}
                nativeButton={false}
              >
                Join
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
    </>
  );
}
