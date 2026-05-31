import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Responsible Disclosure',
  description: 'How to report security vulnerabilities to BidNow responsibly.',
}

const doc: LegalDocument = {
  eyebrow: 'Security · Disclosure',
  title: 'Responsible Disclosure Policy',
  intro:
    'We welcome and appreciate responsible disclosure from security researchers. This policy tells you how to report vulnerabilities, what to expect in return, and our recognition program.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Effective date', v: '2026-05-22' },
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Contact', v: 'support@bidnow.com' },
  ],
  sections: [
    {
      title: 'Our Commitment',
      blocks: [
        {
          type: 'para',
          text: 'BidNow takes the security of our platform and the protection of user data extremely seriously. We commit to: acknowledging all credible reports promptly, investigating findings thoroughly, communicating transparently with reporters throughout the process, and resolving confirmed vulnerabilities in a timely manner.',
        },
        {
          type: 'callout',
          tone: 'green',
          title: 'Safe harbor',
          body: 'We will not initiate legal action against researchers who act in good faith, comply with this policy, avoid privacy violations, do not disrupt services, and do not publicly disclose vulnerabilities before we have had the opportunity to address them.',
        },
      ],
    },
    {
      title: 'Scope',
      blocks: [
        {
          type: 'para',
          text: 'In scope:',
        },
        {
          type: 'bullets',
          items: [
            'bidnow.vn and all subdomains (api.bidnow.vn, admin.bidnow.vn, etc.)',
            'BidNow mobile applications (iOS and Android)',
            'BidNow public APIs',
          ],
        },
        {
          type: 'para',
          text: 'Out of scope:',
        },
        {
          type: 'bullets',
          items: [
            'Third-party services integrated with BidNow (VNPAY, cloud infrastructure providers, identity verification providers)',
            'Social engineering or phishing attacks targeting BidNow employees or users',
            'Denial-of-service (DoS/DDoS) attacks or volumetric attacks',
            'Physical security of BidNow facilities',
            'Vulnerabilities in software versions that BidNow has already been publicly notified about',
          ],
        },
      ],
    },
    {
      title: 'How to Report',
      blocks: [
        {
          type: 'para',
          text: 'Send all vulnerability reports to support@bidnow.com with the subject line: [SECURITY REPORT] - <Brief Description>.',
        },
        {
          type: 'para',
          text: 'Your report should include:',
        },
        {
          type: 'bullets',
          items: [
            'Vulnerability type (e.g., Cross-Site Scripting, SQL Injection, Authentication Bypass, IDOR)',
            'Affected system/URL/endpoint and any relevant parameters',
            'Step-by-step reproduction instructions — the more detailed, the faster we can triage',
            'Potential impact assessment — who is affected and how severely',
            'Proof-of-concept code or screenshots (please avoid accessing, modifying, or exfiltrating data beyond your own test account)',
          ],
        },
        {
          type: 'callout',
          tone: 'indigo',
          title: 'Encrypted reports',
          body: 'To send sensitive details securely, encrypt your report with our PGP public key available at bidnow.vn/pgp-key.txt.',
        },
      ],
    },
    {
      title: 'Response Timeline',
      blocks: [
        {
          type: 'table',
          headers: ['Milestone', 'Target'],
          rows: [
            ['Initial acknowledgment', 'Within 2 business days'],
            ['Triage and severity classification', 'Within 5 business days'],
            ['Status updates during investigation', 'Every 7 days'],
            ['Resolution — Critical / High severity', 'Within 14 days of confirmation'],
            ['Resolution — Medium severity', 'Within 30 days of confirmation'],
            ['Resolution — Low severity', 'Within 90 days of confirmation'],
          ],
        },
      ],
    },
    {
      title: 'Recognition Program',
      blocks: [
        {
          type: 'callout',
          tone: 'indigo',
          title: 'Goodwill recognition program',
          body: 'BidNow currently operates a goodwill recognition program. Monetary bounties are not offered at this time, but we honor researchers who find genuine vulnerabilities.',
        },
        {
          type: 'bullets',
          items: [
            'Critical vulnerabilities: Hall of Fame listing + BidNow premium membership (12 months)',
            'High vulnerabilities: Hall of Fame listing + BidNow premium membership (6 months)',
            'Medium / Low vulnerabilities: Hall of Fame listing',
          ],
        },
      ],
    },
  ],
}

export default function SecurityPage() {
  return <LegalPageTemplate doc={doc} />
}
