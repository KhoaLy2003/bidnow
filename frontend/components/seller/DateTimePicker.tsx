'use client'

import { CalendarIcon } from 'lucide-react'
import { format, startOfDay } from 'date-fns'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

interface DateTimePickerProps {
  id?:          string
  /** Null until the seller picks one. */
  value:        Date | null
  onChange:     (d: Date) => void
  /** Rendered above the field. Leave out when a wrapper already labels it. */
  label?:       string
  placeholder?: string
  /** Days before this one cannot be picked. */
  minDate?:     Date
  invalid?:     boolean
  describedBy?: string
}

/** A date popover plus a time field, editing one Date. A first pick lands on 12:00. */
export function DateTimePicker({
  id, value, onChange, label, placeholder = 'Pick a date and time', minDate, invalid, describedBy,
}: DateTimePickerProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={id}>{label}</Label>}
      <Popover>
        <PopoverTrigger
          render={
            <Button
              id={id}
              variant="outline"
              aria-invalid={invalid || undefined}
              aria-describedby={describedBy}
              className={cn(
                'h-10 w-full justify-start text-left font-normal',
                !value && 'text-muted-foreground',
                invalid && 'border-destructive',
              )}
            />
          }
        >
          <CalendarIcon className="mr-2 h-4 w-4" aria-hidden />
          {value ? format(value, 'PPP p') : <span>{placeholder}</span>}
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="single"
            selected={value ?? undefined}
            defaultMonth={value ?? minDate}
            disabled={minDate ? (d) => d < startOfDay(minDate) : undefined}
            onSelect={(d) => {
              if (!d) return
              const next = new Date(d)
              if (value) next.setHours(value.getHours(), value.getMinutes(), 0, 0)
              else next.setHours(12, 0, 0, 0)
              onChange(next)
            }}
          />
          {value && (
            <div className="border-t border-[var(--color-border-default)] p-3">
              <Label className="mb-2 block text-xs text-muted-foreground">Time</Label>
              <Input
                type="time"
                className="w-full text-sm"
                value={format(value, 'HH:mm')}
                onChange={(e) => {
                  const [h, m] = e.target.value.split(':')
                  if (h === undefined || m === undefined || h === '') return
                  const next = new Date(value)
                  next.setHours(Number(h), Number(m), 0, 0)
                  onChange(next)
                }}
              />
            </div>
          )}
        </PopoverContent>
      </Popover>
    </div>
  )
}
