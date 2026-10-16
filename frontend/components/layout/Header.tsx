"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { LogOut, Plus, Search } from "lucide-react";
import { BidNowMark } from "@/components/shared/BidNowMark";
import { Button } from "@/components/ui/button";
import { NotificationBell } from "@/components/notification/NotificationBell";
import { AccountMenu } from "@/components/layout/AccountMenu";
import { NavLinks } from "@/components/layout/NavLinks";
import { SearchDialog } from "@/components/layout/SearchDialog";
import { NAV_LINKS, ROUTES } from "@/lib/navigation";
import { cn } from "@/lib/utils";

import { useAuthStore } from "@/store/authStore";
import { useBalanceVisibilityStore } from "@/store/balanceVisibilityStore";
import { authService } from "@/services/auth.service";
import { useProfile } from "@/hooks/useProfile";

const CONDENSE_AFTER_PX = 8;

function SearchTrigger({ onClick, className }: { onClick: () => void; className?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="Search lots, sellers and categories"
      className={cn(
        "flex items-center gap-2.5 rounded-[6px] border bg-muted px-3 text-sm text-muted-foreground transition-colors hover:border-foreground/20",
        className,
      )}
    >
      <Search className="size-[18px] shrink-0" aria-hidden />
      <span className="flex-1 truncate text-left">Search lots, sellers, categories</span>
      <kbd className="hidden rounded-[4px] border bg-background px-1.5 py-0.5 font-mono text-[11px] font-medium lg:inline">
        ⌘K
      </kbd>
    </button>
  );
}

export function Header() {
  const router = useRouter();
  const { user, isAuthenticated, logout, refreshToken } = useAuthStore();
  const { profile } = useProfile();
  const [scrolled, setScrolled] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > CONDENSE_AFTER_PX);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setSearchOpen((open) => !open);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const handleLogout = async () => {
    if (refreshToken) {
      await authService.logout(refreshToken, useAuthStore.getState().accessToken);
    }
    useBalanceVisibilityStore.getState().hide();
    logout();
    router.push(ROUTES.login);
  };

  const isAdmin = user?.role === "ADMIN";
  const displayName = profile?.displayName || user?.email?.split("@")[0] || "User";

  return (
    <header
      className={cn(
        "sticky top-0 z-[var(--z-index-sticky)] border-b border-border bg-background transition-shadow duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]",
        scrolled && "shadow-[0_4px_18px_rgb(20_23_31/0.08)]",
      )}
    >
      <div
        className={cn(
          "flex items-center gap-4 px-4 transition-[height] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] md:gap-7 md:px-8",
          scrolled ? "h-[60px]" : "h-[72px]",
        )}
      >
        <Link href={ROUTES.home} aria-label="BidNow home" className="flex shrink-0 items-center gap-2.5">
          <BidNowMark size={32} />
          <span className="font-display text-2xl font-bold leading-none tracking-[-0.06em]">bidnow</span>
        </Link>

        <NavLinks links={NAV_LINKS} />

        <SearchTrigger
          onClick={() => setSearchOpen(true)}
          className="mx-auto hidden h-11 max-w-[440px] flex-[1_1_260px] sm:flex"
        />

        <div className="ml-auto flex shrink-0 items-center gap-2 sm:ml-0">
          {isAuthenticated ? (
            isAdmin ? (
              <Button
                variant="ghost"
                size="sm"
                onClick={handleLogout}
                className="gap-2 text-muted-foreground hover:text-foreground"
              >
                <LogOut className="size-4" />
                Sign out
              </Button>
            ) : (
              <>
                <NotificationBell />
                <AccountMenu
                  name={displayName}
                  email={user?.email ?? ""}
                  avatarUrl={profile?.avatarUrl ?? undefined}
                  verified={user?.isEmailVerified ?? false}
                  onSignOut={handleLogout}
                />
                <Button
                  variant="brand"
                  className="ml-1 hidden h-11 gap-2 rounded-[6px] px-[18px] text-sm font-semibold md:inline-flex"
                  render={<Link href={ROUTES.sell} />}
                  nativeButton={false}
                >
                  <Plus className="size-4" aria-hidden />
                  Sell
                </Button>
              </>
            )
          ) : (
            <>
              <Button
                variant="ghost"
                className="h-11 rounded-[6px] px-4 text-sm"
                render={<Link href={ROUTES.login} />}
                nativeButton={false}
              >
                Sign in
              </Button>
              <Button
                variant="brand"
                className="h-11 rounded-[6px] px-5 text-sm font-semibold"
                render={<Link href={ROUTES.register} />}
                nativeButton={false}
              >
                Join BidNow
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Mobile: search gets its own full-width row */}
      <div className="px-4 pb-3 sm:hidden">
        <SearchTrigger onClick={() => setSearchOpen(true)} className="h-[46px] w-full text-[15px]" />
      </div>

      <SearchDialog open={searchOpen} onOpenChange={setSearchOpen} />
    </header>
  );
}
