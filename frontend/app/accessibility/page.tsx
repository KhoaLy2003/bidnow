import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Accessibility Statement',
  description: 'Our commitment to WCAG 2.1 AA accessibility and how to report issues.',
}

const doc: LegalDocument = {
  eyebrow: 'Company · Accessibility',
  title: 'Accessibility Statement',
  intro:
    'BidNow is committed to ensuring digital accessibility for people with disabilities. We believe everyone should be able to participate in the auction marketplace, regardless of how they access the web.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Target standard', v: 'WCAG 2.1 Level AA' },
    { k: 'Status', v: 'Partially conformant' },
    { k: 'Contact', v: 'support@bidnow.com' },
  ],
  sections: [
    {
      title: 'Conformance Status',
      blocks: [
        {
          type: 'para',
          text: 'BidNow targets WCAG 2.1 Level AA conformance across all pages and features. We conduct accessibility audits semi-annually and address identified issues on a prioritized basis.',
        },
        {
          type: 'callout',
          tone: 'amber',
          title: 'Current status: Partially conformant',
          body: 'This means that some content does not yet meet all WCAG 2.1 Level AA criteria. Known issues are documented in the Known Issues section below.',
        },
      ],
    },
    {
      title: 'Accessibility Features',
      blocks: [
        {
          type: 'para',
          text: 'The following accessibility features are currently implemented or in active development:',
        },
        {
          type: 'bullets',
          items: [
            'Keyboard navigation: All core platform functions — browsing, bidding, account management — are operable by keyboard alone. Tab order follows a logical, consistent reading sequence on all pages.',
            'Skip navigation: A "Skip to main content" link is the first focusable element on every page, allowing keyboard and screen reader users to bypass repetitive navigation.',
            'Semantic HTML: Pages use semantic HTML5 elements (<main>, <nav>, <header>, <section>, <article>) to communicate structure to assistive technologies.',
            'ARIA roles and labels: Interactive components use ARIA roles, states, and properties where native HTML semantics are insufficient.',
            'Alt text: All meaningful images include descriptive alternative text. Decorative images have null alt attributes.',
            'Focus indicators: Clearly visible focus rings are applied to all interactive elements and respect the system\'s focus visibility preferences.',
            'Color contrast: Text-to-background contrast ratios meet WCAG 2.1 AA minimums: 4.5:1 for body text, 3:1 for large text and UI components.',
            'Resizable text: All text can be resized up to 200% using browser zoom without loss of content, functionality, or horizontal scrolling.',
            'Reduced motion: All animations and transitions respect the prefers-reduced-motion CSS media query.',
            'Form labels: All form inputs have visible, programmatically associated labels.',
          ],
        },
      ],
    },
    {
      title: 'Keyboard Shortcuts',
      blocks: [
        {
          type: 'table',
          headers: ['Action', 'Keyboard Shortcut'],
          rows: [
            ['Skip to main content', 'Tab — first tab stop on any page'],
            ['Open bid input panel', 'B — on auction detail pages'],
            ['Confirm / place bid', 'Enter — when bid input is focused'],
            ['Close any open dialog or modal', 'Esc'],
            ['Navigate auction image gallery', '← Left Arrow / → Right Arrow'],
            ['Navigate dropdown menus', '↑ Up Arrow / ↓ Down Arrow'],
          ],
        },
      ],
    },
    {
      title: 'Screen Reader Compatibility',
      blocks: [
        {
          type: 'para',
          text: 'BidNow has been tested with the following screen reader and browser combinations:',
        },
        {
          type: 'table',
          headers: ['Screen Reader', 'Browser', 'Platform'],
          rows: [
            ['NVDA (latest)', 'Google Chrome', 'Windows'],
            ['JAWS (latest)', 'Google Chrome', 'Windows'],
            ['VoiceOver', 'Safari', 'macOS'],
            ['VoiceOver', 'Safari', 'iOS'],
            ['TalkBack', 'Google Chrome', 'Android'],
          ],
        },
      ],
    },
    {
      title: 'Known Accessibility Issues',
      blocks: [
        {
          type: 'para',
          text: 'The following known issues are being actively worked on:',
        },
        {
          type: 'numbered',
          items: [
            'Live auction countdown timers use aria-live="assertive" regions, which announce time updates every second. This can be verbose for screen reader users. We are implementing a rate-limited announcement that updates every 30 seconds.',
            'Dynamically loaded bid history may not announce new entries immediately in all screen reader and browser combinations. We are reviewing our live region implementation.',
            'Third-party embedded content (including some VNPAY payment widgets) may not be fully accessible. We are working with our payment provider on remediation.',
          ],
        },
      ],
    },
    {
      title: 'Feedback & Contact',
      blocks: [
        {
          type: 'callout',
          tone: 'green',
          title: 'We take all accessibility feedback seriously',
          body: 'If you experience barriers, cannot access any content, or have suggestions for improvement, please email support@bidnow.com with the subject line "Accessibility Feedback". We will respond within 5 business days and work with you to find an accessible alternative or address the issue.',
        },
      ],
    },
  ],
}

export default function AccessibilityPage() {
  return <LegalPageTemplate doc={doc} />
}
