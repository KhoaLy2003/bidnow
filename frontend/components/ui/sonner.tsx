"use client"

import { useTheme } from "next-themes"
import { Toaster as Sonner, type ToasterProps } from "sonner"
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon, OctagonXIcon, Loader2Icon } from "lucide-react"

const Toaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme()

  return (
    <Sonner
      theme={theme as ToasterProps["theme"]}
      richColors
      className="toaster group"
      icons={{
        success: (
          <CircleCheckIcon className="size-4" />
        ),
        info: (
          <InfoIcon className="size-4" />
        ),
        warning: (
          <TriangleAlertIcon className="size-4" />
        ),
        error: (
          <OctagonXIcon className="size-4" />
        ),
        loading: (
          <Loader2Icon className="size-4 animate-spin" />
        ),
      }}
      style={
        {
          "--normal-bg":   "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)",
          "--success-bg":     "var(--color-success-subtle)",
          "--success-text":   "var(--color-success-text)",
          "--success-border": "var(--color-success-border)",
          "--error-bg":       "var(--color-danger-subtle)",
          "--error-text":     "var(--color-danger-text)",
          "--error-border":   "var(--color-danger-border)",
          "--warning-bg":     "var(--color-warning-subtle)",
          "--warning-text":   "var(--color-warning-text)",
          "--warning-border": "var(--color-warning-border)",
          "--info-bg":        "var(--color-info-subtle)",
          "--info-text":      "var(--color-info-text)",
          "--info-border":    "var(--color-info-border)",
        } as React.CSSProperties
      }
      toastOptions={{
        classNames: {
          toast: "cn-toast",
        },
      }}
      {...props}
    />
  )
}

export { Toaster }
