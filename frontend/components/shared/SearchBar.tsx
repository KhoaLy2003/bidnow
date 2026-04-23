'use client'

import { useState, useRef, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import { Search, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface SearchBarProps {
  defaultValue?: string
  placeholder?:  string
  className?:    string
}

export function SearchBar({
  defaultValue = '',
  placeholder  = 'Search auctions…',
  className,
}: SearchBarProps) {
  const router   = useRouter()
  const inputRef = useRef<HTMLInputElement>(null)
  const [value, setValue] = useState(defaultValue)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const q = value.trim()
    if (q) router.push(`/auctions?q=${encodeURIComponent(q)}`)
  }

  return (
    <form onSubmit={handleSubmit} className={cn('relative flex items-center', className)}>
      <Search className="absolute left-2.5 size-4 text-muted-foreground pointer-events-none" />
      <Input
        ref={inputRef}
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        className="h-9 pl-8 pr-8"
      />
      {value && (
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          className="absolute right-1"
          onClick={() => { setValue(''); inputRef.current?.focus() }}
        >
          <X className="size-3" />
          <span className="sr-only">Clear</span>
        </Button>
      )}
    </form>
  )
}
