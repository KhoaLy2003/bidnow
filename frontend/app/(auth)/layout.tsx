import Link from 'next/link'
import { Gavel } from 'lucide-react'

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-[var(--color-bg-elevated)] px-4">
      <Link href="/" className="mb-8 flex items-center gap-2">
        <Gavel className="size-6 text-[var(--color-text-brand)]" />
        <span className="text-xl font-semibold">
          Bid<span className="text-[var(--color-text-brand)] font-bold">Now</span>
        </span>
      </Link>
      {children}
    </div>
  )
}
