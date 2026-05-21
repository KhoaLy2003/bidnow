'use client'

import { useState } from 'react'
import { CalendarIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatDate } from '@/lib/utils'
import { cn } from '@/lib/utils'

interface DateRangePickerProps {
  fromDate: Date | undefined
  toDate: Date | undefined
  onChange: (dates: { from?: Date; to?: Date }) => void
}

export function DateRangePicker({ fromDate, toDate, onChange }: DateRangePickerProps) {
  const [fromMonth, setFromMonth] = useState<Date>(fromDate || new Date())
  const [toMonth, setToMonth] = useState<Date>(toDate || new Date())
  const [fromOpen, setFromOpen] = useState(false)
  const [toOpen, setToOpen] = useState(false)

  return (
    <div className="flex gap-2 w-full">
      <Popover open={fromOpen} onOpenChange={setFromOpen}>
        <PopoverTrigger
          className="flex-1"
          render={
            <Button
              variant="outline"
              className={cn(
                'w-full justify-start text-left font-normal',
                !fromDate && 'text-muted-foreground',
              )}
            >
              <CalendarIcon className="mr-2 h-4 w-4" />
              {fromDate ? formatDate(fromDate.toISOString()) : 'From'}
            </Button>
          }
        />
        <PopoverContent className="w-auto p-0">
          <Calendar
            mode="single"
            selected={fromDate}
            onSelect={(date) => {
              const newTo = date && toDate && date > toDate ? undefined : toDate
              onChange({ from: date, to: newTo })
              setFromOpen(false)
            }}
            month={fromMonth}
            onMonthChange={setFromMonth}
          />
        </PopoverContent>
      </Popover>

      <Popover open={toOpen} onOpenChange={setToOpen}>
        <PopoverTrigger
          className="flex-1"
          render={
            <Button
              variant="outline"
              className={cn(
                'w-full justify-start text-left font-normal',
                !toDate && 'text-muted-foreground',
              )}
            >
              <CalendarIcon className="mr-2 h-4 w-4" />
              {toDate ? formatDate(toDate.toISOString()) : 'To'}
            </Button>
          }
        />
        <PopoverContent className="w-auto p-0">
          <Calendar
            mode="single"
            selected={toDate}
            onSelect={(date) => {
              onChange({ from: fromDate, to: date })
              setToOpen(false)
            }}
            month={toMonth}
            onMonthChange={setToMonth}
          />
        </PopoverContent>
      </Popover>
    </div>
  )
}
