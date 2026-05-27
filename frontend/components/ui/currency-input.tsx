import * as React from "react"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"

export interface CurrencyInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'> {
  valueCents: number
  onChangeCents: (cents: number) => void
  symbol?: string
  hasError?: boolean
}

export function CurrencyInput({
  valueCents,
  onChangeCents,
  symbol = "$",
  hasError,
  className,
  ...props
}: CurrencyInputProps) {
  const [raw, setRaw] = React.useState(valueCents > 0 ? (valueCents / 100).toString() : '')

  React.useEffect(() => {
    // Keep local string state in sync if external value resets to 0
    if (valueCents === 0 && raw !== '') {
      setRaw('')
    }
    // Also sync if external value gets a value that doesn't match our current raw
    // e.g. when loading data from API
    const currentDollars = parseFloat(raw)
    const expectedDollars = valueCents / 100
    if (!isNaN(expectedDollars) && expectedDollars !== currentDollars && valueCents > 0) {
      setRaw(expectedDollars.toString())
    }
  }, [valueCents]) // Removed 'raw' from deps to avoid loop

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setRaw(e.target.value)
    const dollars = parseFloat(e.target.value)
    onChangeCents(isNaN(dollars) ? 0 : Math.round(dollars * 100))
  }

  return (
    <div className="relative">
      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none">
        {symbol}
      </span>
      <Input
        type="number"
        step="0.01"
        min="0"
        value={raw}
        onChange={handleChange}
        className={cn('pl-7 font-mono text-sm', hasError && 'border-destructive', className)}
        {...props}
      />
    </div>
  )
}
