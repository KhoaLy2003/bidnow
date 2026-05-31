import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Terms of Service',
  description: 'The legal agreement governing your use of the BidNow platform.',
}

const doc: LegalDocument = {
  eyebrow: 'Legal · Terms',
  title: 'Terms of Service',
  intro:
    'By accessing or using BidNow you agree to these Terms — a binding legal agreement between you and BidNow Co., Ltd. Read them in full before you bid.',
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
      title: 'Acceptance of Terms',
      blocks: [
        {
          type: 'para',
          text: 'By accessing or using BidNow (the "Platform"), you agree to be bound by these Terms of Service ("Terms"). If you do not agree, you may not use the Platform. BidNow reserves the right to modify these Terms at any time; continued use following notice of any change constitutes acceptance of the updated Terms.',
        },
        {
          type: 'callout',
          tone: 'indigo',
          title: 'Key change in v1.0',
          body: 'First public release. Replaces the closed-beta agreement signed at sign-up. Beta users were notified by email on 2026-05-08.',
        },
      ],
    },
    {
      title: 'User Accounts & Registration',
      blocks: [
        {
          type: 'bullets',
          items: [
            'You must be at least 18 years of age and legally capable of entering binding contracts.',
            'Provide accurate, complete, and current information at registration and keep it updated.',
            'You are solely responsible for maintaining the confidentiality of your credentials and for all activity under your account.',
            'Each individual may hold only one active BidNow account. Duplicate or fraudulent accounts are strictly prohibited.',
            'BidNow may suspend or terminate accounts that violate these Terms without prior notice.',
          ],
        },
      ],
    },
    {
      title: 'Auction Rules & Bidding',
      blocks: [
        {
          type: 'para',
          text: 'All bids are binding offers to purchase. Placing a bid constitutes a legal commitment to pay the full bid amount if you are the winning bidder. The following rules govern every auction on the Platform:',
        },
        {
          type: 'bullets',
          items: [
            'Bids may not be retracted once placed, except in cases of clear, demonstrable typographical error, at BidNow\'s sole discretion.',
            'Auction end times may be automatically extended (anti-sniping) if a bid is placed within the final 3 minutes. The auction closes only after 3 full minutes pass with no new bid.',
            'Reserve prices, if set by sellers, must be met for an auction to result in a completed sale. The existence of a reserve is disclosed on every listing.',
            'BidNow may cancel or void any auction or bid that violates platform policies.',
          ],
        },
      ],
    },
    {
      title: 'Payment & Refund Policy',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Winning bidders must complete payment within 48 hours of auction close.',
            'Bid deposits collected for qualifying auctions are refundable on non-win, subject to the Return & Refund Policy.',
            "All payments are processed through VNPAY. Completing a payment indicates agreement to VNPAY's applicable terms.",
            'Failure to complete payment after winning may result in deposit forfeiture and account suspension.',
            'BidNow does not store full payment card numbers; processing is handled by PCI-DSS-compliant providers.',
          ],
        },
      ],
    },
    {
      title: 'Intellectual Property',
      blocks: [
        {
          type: 'para',
          text: 'All content on the Platform — logos, design elements, software, and text — is the property of BidNow or its licensors and is protected under Vietnamese intellectual property law and applicable international copyright conventions. Sellers grant BidNow a non-exclusive, worldwide, royalty-free licence to display, reproduce, and promote their auction listings on the Platform and in marketing materials.',
        },
      ],
    },
    {
      title: 'Limitation of Liability',
      blocks: [
        {
          type: 'callout',
          tone: 'amber',
          title: 'Read this section carefully',
          body: 'It limits the amount BidNow may be liable to you for. Vietnamese consumer protection law continues to apply where it provides stronger protections than these Terms.',
        },
        {
          type: 'bullets',
          items: [
            'BidNow provides the Platform on an "as-is" and "as-available" basis without warranties of any kind.',
            'BidNow shall not be liable for indirect, incidental, special, punitive, or consequential damages.',
            'Total aggregate liability for any claim shall not exceed the total fees paid by you to BidNow in the 12 months preceding the claim, or 1,000,000 VND, whichever is greater.',
            'BidNow is not responsible for the actions, representations, listings, or products of third-party sellers.',
          ],
        },
      ],
    },
    {
      title: 'Indemnification',
      blocks: [
        {
          type: 'para',
          text: 'You agree to indemnify, defend, and hold harmless BidNow, its officers, directors, employees, agents, and licensors from any claims, damages, losses, liabilities, costs, and expenses (including reasonable attorneys\' fees) arising from (a) your use of the Platform, (b) your violation of these Terms, (c) your violation of any third-party right, or (d) any content or listing you submit.',
        },
      ],
    },
    {
      title: 'Governing Law',
      blocks: [
        {
          type: 'para',
          text: 'These Terms are governed by the laws of the Socialist Republic of Vietnam, including the Law on Electronic Commerce (No. 51/2005/QH11, as amended), the Law on Consumer Protection (No. 59/2010/QH12), and applicable regulations of the Ministry of Industry and Trade. Disputes that cannot be resolved through negotiation shall be submitted to the exclusive jurisdiction of the competent People\'s Court in Ho Chi Minh City, Vietnam.',
        },
      ],
    },
  ],
}

const related = [
  { tag: 'Policy', title: 'Privacy Policy', sub: 'How we collect and use your data', href: '/privacy' },
  { tag: 'Policy', title: 'Return & Refund Policy', sub: 'Deposit rules and dispute timelines', href: '/refund-policy' },
  { tag: 'Policy', title: 'Community Guidelines', sub: 'Conduct rules for buyers and sellers', href: '/community-guidelines' },
]

export default function TermsPage() {
  return <LegalPageTemplate doc={doc} related={related} />
}
