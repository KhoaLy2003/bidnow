import * as React from "react"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"

export interface CurrencyInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'> {
  value:    number
  onChange: (dollars: number) => void
  symbol?:  string
  hasError?: boolean
}

export function CurrencyInput({
  value,
  onChange,
  symbol = "$",
  hasError,
  className,
  ...props
}: CurrencyInputProps) {
  const [raw, setRaw] = React.useState(value > 0 ? value.toString() : '')

  React.useEffect(() => {
    if (value === 0 && raw !== '') {
      setRaw('')
      return
    }
    const currentDollars = Number.parseFloat(raw)
    if (value > 0 && value !== currentDollars) {
      setRaw(value.toString())
    }
  }, [value]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setRaw(e.target.value)
    const dollars = Number.parseFloat(e.target.value)
    onChange(isNaN(dollars) ? 0 : dollars)
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
