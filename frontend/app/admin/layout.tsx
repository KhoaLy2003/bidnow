'use client'

import React from 'react'
import { AdminGuard } from '@/components/shared/AdminGuard'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Users, LayoutDashboard, History, Settings } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

const sidebarItems = [
  { icon: LayoutDashboard, label: 'Dashboard', href: '/admin' },
  { icon: Users, label: 'User Management', href: '/admin/users' },
  { icon: History, label: 'Auction Monitoring', href: '/admin/auctions' },
  { icon: Settings, label: 'System Settings', href: '/admin/settings' },
]

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <AdminGuard>
      <div className="flex flex-col min-h-screen bg-background">
        <Header />
        <div className="mx-auto w-full max-w-[var(--container-xl)] flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="grid min-h-[70vh] gap-6 md:grid-cols-[260px_minmax(0,1fr)]">
            <aside>
              <Card className="sticky top-24">
                <CardHeader>
                  <CardTitle>Admin Panel</CardTitle>
                  <CardDescription>Platform moderation controls</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <Separator />
              
                  <nav className="flex flex-col gap-1">
                    {sidebarItems.map((item) => {
                      const isActive = pathname === item.href
                      return (
                        <Button
                          key={item.href}
                          variant={isActive ? 'secondary' : 'ghost'}
                          className={cn(
                            'justify-start',
                            isActive ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'
                          )}
                          render={<Link href={item.href} />}
                          nativeButton={false}
                        >
                          <item.icon className="mr-2 size-4" />
                          {item.label}
                        </Button>
                      )
                    })}
                  </nav>
                </CardContent>
              </Card>
            </aside>

            <main className="flex-1 min-w-0">
              <div className="min-h-full">
                {children}
              </div>
            </main>
          </div>
        </div>
        <Footer />
      </div>
    </AdminGuard>
  )
}
