import { Suspense } from 'react'
import { PurchasesView } from '@/components/purchase/PurchasesView'

export default function PurchasesPage() {
  // PurchasesView reads the URL (filter and page), which needs a Suspense boundary during prerender.
  return (
    <Suspense>
      <PurchasesView />
    </Suspense>
  )
}
