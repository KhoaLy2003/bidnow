import Link from 'next/link'
import { BidNowGavelMark } from '@/components/shared/BidNowGavelMark'

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-[var(--color-bg-elevated)] px-4">
      <Link href="/" className="mb-8 flex items-center gap-2">
        <BidNowGavelMark size={32} />
        <span className="text-xl font-medium">
          Bid<span className="text-[var(--color-text-brand)] font-medium">Now</span>
        </span>
      </Link>
      {children}
    </div>
  )
}
