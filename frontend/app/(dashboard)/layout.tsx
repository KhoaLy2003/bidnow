import { Header }    from '@/components/layout/Header'
import { Footer }    from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { AuthGuard } from '@/components/shared/AuthGuard'

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <Header />
      <div className="flex-1 mx-auto w-full max-w-[var(--container-xl)] px-4 py-8 pb-14 md:pb-8">
        {children}
      </div>
      <Footer />
      <BottomNav />
    </AuthGuard>
  )
}
