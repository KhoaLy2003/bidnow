export function PanelFooter() {
  return (
    <div className="flex items-center justify-end gap-3 px-[18px] py-3 border-t text-xs text-muted-foreground bg-[var(--color-bg-elevated)]">
      <button type="button" className="underline underline-offset-2">Share</button>
      <button type="button" className="underline underline-offset-2">Save</button>
    </div>
  )
}
