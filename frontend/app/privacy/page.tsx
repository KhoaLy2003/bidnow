import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Privacy Policy',
  description: 'How BidNow collects, uses, and protects your personal data.',
}

const doc: LegalDocument = {
  eyebrow: 'Legal · Privacy',
  title: 'Privacy Policy',
  intro:
    'BidNow is committed to protecting your personal data. This policy explains what we collect, why we collect it, and how you can exercise your rights.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Effective date', v: '2026-05-22' },
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Version', v: '1.0' },
    { k: 'Jurisdiction', v: 'Vietnam' },
  ],
  sections: [
    {
      title: 'Information We Collect',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Account Data: Full name, email address, phone number, date of birth, and government-issued ID for identity verification.',
            'Transaction Data: Bid history, purchase history, payment method tokens, deposit records, and withdrawal records.',
            'Identity Verification Data: Copies of government-issued identification (CCCD/CMND or passport) as required under Vietnamese e-commerce regulations.',
            'Usage Data: IP addresses, browser type, device identifiers, pages visited, timestamps, referring URLs, and clickstream data.',
            'Communications: Messages sent through our contact form, support channels, or feedback tools.',
            'Cookies & Tracking: Session cookies, preference cookies, and analytics cookies.',
          ],
        },
      ],
    },
    {
      title: 'How We Use Your Information',
      blocks: [
        {
          type: 'bullets',
          items: [
            'To operate, maintain, and improve the Platform and its features.',
            'To process transactions, verify identity, prevent fraud, and enforce our policies.',
            'To send transactional communications: order confirmations, bid notifications, outbid alerts, and winning notifications.',
            'To send marketing communications where you have provided explicit consent. You may withdraw consent at any time.',
            'To comply with legal obligations under Vietnamese law, including tax reporting and anti-money-laundering requirements.',
            'To resolve disputes and enforce our Terms of Service and Community Guidelines.',
            'To analyze aggregated usage patterns and improve user experience.',
          ],
        },
      ],
    },
    {
      title: 'Data Sharing & Third Parties',
      blocks: [
        {
          type: 'para',
          text: 'We share your personal data only in the following circumstances:',
        },
        {
          type: 'bullets',
          items: [
            "VNPAY: We share billing information necessary for payment processing. VNPAY's privacy policy governs their use of your data.",
            'Identity Verification Providers: Third-party services used to verify government-issued ID documents receive your ID data solely for verification purposes.',
            'Analytics Services: Aggregated, anonymized usage data may be shared with analytics providers to help us understand platform usage.',
            'Legal Requirements: We disclose data when required by Vietnamese law, valid court order, or legitimate government authority request.',
            'Business Transfers: In the event of a merger, acquisition, or sale of substantially all assets, your data may be transferred subject to equivalent privacy protections.',
            'We do not sell, rent, or trade your personal data to any third party for their independent marketing purposes.',
          ],
        },
      ],
    },
    {
      title: 'Data Retention',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Account data is retained for the duration of your account plus 5 years following account closure, as required by Vietnamese e-commerce regulations.',
            'Transaction and payment records are retained for 10 years to comply with tax and financial audit requirements.',
            'Usage logs and analytics data are retained for 2 years.',
            'Identity verification documents are retained for 5 years following the last transaction, in compliance with anti-money-laundering regulations.',
            'You may request deletion of your account at any time; deletion is carried out within 30 days subject to our mandatory legal retention obligations.',
          ],
        },
      ],
    },
    {
      title: 'User Rights & Access',
      blocks: [
        {
          type: 'callout',
          tone: 'indigo',
          title: 'Your rights under Vietnamese law (Decree No. 13/2023/ND-CP) and GDPR',
          body: 'The following rights apply to your personal data held by BidNow. To exercise any right, email support@bidnow.com. We will respond within 30 days.',
        },
        {
          type: 'bullets',
          items: [
            'Access: Request a copy of the personal data we hold about you.',
            'Rectification: Correct inaccurate or incomplete personal data.',
            'Erasure: Request deletion of your data, subject to legal retention requirements.',
            'Objection: Object to processing of your data for direct marketing purposes.',
            'Portability: Receive your data in a structured, machine-readable format.',
            'Withdrawal of Consent: Withdraw consent at any time without affecting the lawfulness of prior processing.',
          ],
        },
      ],
    },
    {
      title: 'Cookies & Tracking',
      blocks: [
        {
          type: 'para',
          text: 'We use the following categories of cookies:',
        },
        {
          type: 'bullets',
          items: [
            'Strictly Necessary: Session management and authentication. Required for the Platform to function. Cannot be disabled.',
            'Functional: Remembering your language, display preferences, and recently viewed auctions.',
            'Analytics: Understanding how users interact with the Platform to improve features. Opt-out available via the cookie preference center.',
            'Security: Fraud detection signals and CSRF token protection.',
          ],
        },
        {
          type: 'para',
          text: 'You can manage non-essential cookies through the Cookie Preference Center accessible from the footer of every page.',
        },
      ],
    },
    {
      title: 'Security Measures',
      blocks: [
        {
          type: 'bullets',
          items: [
            'All data transmissions are encrypted using TLS 1.2 or higher.',
            'Passwords are hashed using bcrypt with a minimum cost factor of 12.',
            'Access to personal data is restricted to authorized personnel on a need-to-know basis, enforced by role-based access controls.',
            'We conduct regular security audits and periodic penetration tests.',
            'In the event of a personal data breach, we will notify affected users and the relevant Vietnamese authority within the timeframes required by Decree No. 13/2023/ND-CP.',
          ],
        },
      ],
    },
    {
      title: 'Contact Information',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Email: support@bidnow.com',
            'Data Controller: BidNow Co., Ltd., Ho Chi Minh City, Vietnam',
            'Response Time: Within 30 calendar days of receipt.',
          ],
        },
      ],
    },
  ],
}

const related = [
  { tag: 'Legal', title: 'Terms of Service', sub: 'The binding agreement governing platform use', href: '/terms' },
  { tag: 'Policy', title: 'Anti-Fraud Policy', sub: 'How we detect and prevent fraud', href: '/anti-fraud' },
  { tag: 'Policy', title: 'Accessibility Statement', sub: 'Our commitment to inclusive design', href: '/accessibility' },
]

export default function PrivacyPage() {
  return <LegalPageTemplate doc={doc} related={related} />
}
