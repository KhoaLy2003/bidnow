import { Header }    from '@/components/layout/Header'
import { Footer }    from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Header />
      <div className="flex-1 mx-auto w-full max-w-[var(--container-xl)] px-4 py-8 pb-14 md:pb-8">
        {children}
      </div>
      <Footer />
      <BottomNav />
    </>
  )
}
