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
  const [prevValue, setPrevValue] = React.useState(value)

  if (value !== prevValue) {
    setPrevValue(value)
    if (value === 0) {
      setRaw('')
    } else if (value !== Number.parseFloat(raw)) {
      setRaw(value.toString())
    }
  }

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
