'use client'

import { useSyncExternalStore } from 'react'

const subscribe = () => () => {}
const getSnapshot = () => true
const getServerSnapshot = () => false

// True only on the client, after hydration — avoids SSR/CSR mismatches
// for client-only UI without the extra render caused by a mount effect.
export function useHasMounted() {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
}
