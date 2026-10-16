// Each auth page renders its own <AuthShell> so the brand panel can carry page-specific copy.
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>
}
