interface WatchingFooterProps {
  n: number
}

export function WatchingFooter({ n }: WatchingFooterProps) {
  return (
    <div className="flex items-center justify-between px-[18px] py-3 border-t text-xs text-muted-foreground bg-[var(--color-bg-elevated)]">
      <span>
        <span className="font-mono text-foreground">{n}</span> watching
      </span>
      <span className="flex gap-3">
        <span className="underline underline-offset-2 cursor-pointer">Share</span>
        <span className="underline underline-offset-2 cursor-pointer">Save</span>
      </span>
    </div>
  )
}
