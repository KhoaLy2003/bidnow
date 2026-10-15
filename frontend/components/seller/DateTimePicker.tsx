'use client'

import { CalendarIcon } from 'lucide-react'
import { format } from 'date-fns'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface DateTimePickerProps {
  id?:      string
  value:    Date
  onChange: (d: Date) => void
  label:    string
}

/** A date popover plus a time field, editing one Date. */
export function DateTimePicker({ id, value, onChange, label }: DateTimePickerProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Popover>
        <PopoverTrigger
          render={
            <Button id={id} variant="outline" className="h-10 w-full justify-start text-left font-normal" />
          }
        >
          <CalendarIcon className="mr-2 h-4 w-4" aria-hidden />
          {format(value, 'PPP p')}
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="single"
            selected={value}
            onSelect={(d) => {
              if (!d) return
              const next = new Date(d)
              next.setHours(value.getHours(), value.getMinutes())
              onChange(next)
            }}
          />
          <div className="border-t border-[var(--color-border-default)] p-3">
            <Label className="mb-2 block text-xs text-muted-foreground">Time</Label>
            <Input
              type="time"
              className="w-full text-sm"
              value={format(value, 'HH:mm')}
              onChange={(e) => {
                const [h, m] = e.target.value.split(':')
                const next = new Date(value)
                next.setHours(Number(h), Number(m))
                onChange(next)
              }}
            />
          </div>
        </PopoverContent>
      </Popover>
    </div>
  )
}
