export enum AuctionStatus {
  Draft      = 'draft',
  Scheduled  = 'scheduled',
  Active     = 'active',
  EndingSoon = 'ending-soon',
  Critical   = 'critical',
  Closed     = 'closed',
  Won        = 'won',
  Lost       = 'lost',
  Outbid     = 'outbid',
}

export interface StatusTokens {
  bg: string
  text: string
  border: string
  accent: string
}

export function getStatusTokens(status: AuctionStatus): StatusTokens {
  const map: Record<AuctionStatus, StatusTokens> = {
    [AuctionStatus.Draft]: {
      bg:     'var(--color-auction-draft-bg)',
      text:   'var(--color-auction-draft-text)',
      border: 'var(--color-auction-draft-border)',
      accent: 'var(--color-auction-draft-accent)',
    },
    [AuctionStatus.Scheduled]: {
      bg:     'var(--color-auction-scheduled-bg)',
      text:   'var(--color-auction-scheduled-text)',
      border: 'var(--color-auction-scheduled-border)',
      accent: 'var(--color-auction-scheduled-accent)',
    },
    [AuctionStatus.Active]: {
      bg:     'var(--color-auction-active-bg)',
      text:   'var(--color-auction-active-text)',
      border: 'var(--color-auction-active-border)',
      accent: 'var(--color-auction-active-accent)',
    },
    [AuctionStatus.EndingSoon]: {
      bg:     'var(--color-auction-ending-bg)',
      text:   'var(--color-auction-ending-text)',
      border: 'var(--color-auction-ending-border)',
      accent: 'var(--color-auction-ending-accent)',
    },
    [AuctionStatus.Critical]: {
      bg:     'var(--color-auction-critical-bg)',
      text:   'var(--color-auction-critical-text)',
      border: 'var(--color-auction-critical-border)',
      accent: 'var(--color-auction-critical-accent)',
    },
    [AuctionStatus.Closed]: {
      bg:     'var(--color-auction-closed-bg)',
      text:   'var(--color-auction-closed-text)',
      border: 'var(--color-auction-closed-border)',
      accent: 'var(--color-auction-closed-accent)',
    },
    [AuctionStatus.Won]: {
      bg:     'var(--color-auction-won-bg)',
      text:   'var(--color-auction-won-text)',
      border: 'var(--color-auction-won-border)',
      accent: 'var(--color-auction-won-accent)',
    },
    [AuctionStatus.Lost]: {
      bg:     'var(--color-auction-lost-bg)',
      text:   'var(--color-auction-lost-text)',
      border: 'var(--color-auction-lost-border)',
      accent: 'var(--color-auction-lost-accent)',
    },
    [AuctionStatus.Outbid]: {
      bg:     'var(--color-auction-outbid-bg)',
      text:   'var(--color-auction-outbid-text)',
      border: 'var(--color-auction-outbid-border)',
      accent: 'var(--color-auction-outbid-accent)',
    },
  }
  return map[status]
}

export const colors = {
  brand: {
    50:  'var(--brand-50)',
    100: 'var(--brand-100)',
    200: 'var(--brand-200)',
    300: 'var(--brand-300)',
    400: 'var(--brand-400)',
    500: 'var(--brand-500)',
    600: 'var(--brand-600)',
    700: 'var(--brand-700)',
    800: 'var(--brand-800)',
    900: 'var(--brand-900)',
  },
  text: {
    primary:   'var(--color-text-primary)',
    secondary: 'var(--color-text-secondary)',
    tertiary:  'var(--color-text-tertiary)',
    disabled:  'var(--color-text-disabled)',
    inverse:   'var(--color-text-inverse)',
    brand:     'var(--color-text-brand)',
    link:      'var(--color-text-link)',
    linkHover: 'var(--color-text-link-hover)',
  },
  bg: {
    base:      'var(--color-bg-base)',
    elevated:  'var(--color-bg-elevated)',
    overlay:   'var(--color-bg-overlay)',
    card:      'var(--color-bg-card)',
    cardHover: 'var(--color-bg-card-hover)',
    modal:     'var(--color-bg-modal)',
    backdrop:  'var(--color-bg-backdrop)',
    sidebar:   'var(--color-bg-sidebar)',
  },
  success: {
    default: 'var(--color-success-default)',
    subtle:  'var(--color-success-subtle)',
    border:  'var(--color-success-border)',
    text:    'var(--color-success-text)',
  },
  warning: {
    default: 'var(--color-warning-default)',
    subtle:  'var(--color-warning-subtle)',
    border:  'var(--color-warning-border)',
    text:    'var(--color-warning-text)',
  },
  danger: {
    default: 'var(--color-danger-default)',
    subtle:  'var(--color-danger-subtle)',
    border:  'var(--color-danger-border)',
    text:    'var(--color-danger-text)',
  },
  info: {
    default: 'var(--color-info-default)',
    subtle:  'var(--color-info-subtle)',
    border:  'var(--color-info-border)',
    text:    'var(--color-info-text)',
  },
  wallet: {
    bg:       'var(--color-wallet-bg)',
    text:     'var(--color-wallet-text)',
    icon:     'var(--color-wallet-icon)',
    positive: 'var(--color-wallet-positive)',
    negative: 'var(--color-wallet-negative)',
  },
  shadow: {
    brand:   'var(--shadow-brand)',
    danger:  'var(--shadow-danger)',
    success: 'var(--shadow-success)',
  },
} as const

export const durations = {
  instant:   0,
  fast:      80,
  normal:    150,
  moderate:  250,
  slow:      350,
  extraSlow: 500,
  bidPulse:  600,
  countdown: 1000,
} as const

export const easings = {
  spring:    'cubic-bezier(0.34, 1.56, 0.64, 1)',
  snappy:    'cubic-bezier(0.2, 0, 0, 1)',
  bounceOut: 'cubic-bezier(0.68, -0.55, 0.27, 1.55)',
  in:        'cubic-bezier(0.4, 0, 1, 1)',
  out:       'cubic-bezier(0, 0, 0.2, 1)',
  inOut:     'cubic-bezier(0.4, 0, 0.2, 1)',
} as const
