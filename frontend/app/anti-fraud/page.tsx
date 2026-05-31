import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Anti-Fraud Policy',
  description: "BidNow's multi-layered approach to detecting and preventing fraud.",
}

const doc: LegalDocument = {
  eyebrow: 'Policy · Anti-Fraud',
  title: 'Anti-Fraud Policy',
  intro:
    'BidNow uses a multi-layered approach to prevent, detect, and respond to fraud. This policy explains what we protect against, how we do it, and what happens when violations are confirmed.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Report fraud', v: 'support@bidnow.com' },
  ],
  sections: [
    {
      title: 'Types of Fraud We Prevent',
      blocks: [
        {
          type: 'bullets',
          items: [
            "Shill bidding: Sellers or their associates placing artificial bids to inflate auction prices.",
            "Non-delivery fraud: Sellers accepting payment without delivering the item.",
            "Item misrepresentation: Falsely describing an item's condition, authenticity, age, or provenance.",
            "Payment fraud: Using stolen credit cards, compromised bank accounts, or fraudulent payment credentials.",
            "Identity fraud: Registering accounts using false, stolen, or fabricated identities.",
            "Feedback manipulation: Using fake accounts or coordinated reviews to artificially inflate a seller's ratings.",
            "Counterfeit goods: Listing or selling items that are falsely represented as genuine branded products.",
            "Chargeback fraud: Completing a legitimate purchase and then disputing the charge with a bank to obtain a refund while keeping the item.",
          ],
        },
      ],
    },
    {
      title: 'Detection Methods',
      blocks: [
        {
          type: 'para',
          text: 'BidNow uses a multi-layered approach to fraud detection:',
        },
        {
          type: 'bullets',
          items: [
            'Behavioral analytics: Automated systems monitor for unusual bidding patterns, account activity spikes, and anomalous transaction sequences.',
            'Device and network fingerprinting: IP addresses and device identifiers are analyzed to detect duplicate or linked accounts.',
            'Transaction monitoring: Payment amounts, frequencies, and patterns are monitored for signs of money laundering or payment fraud.',
            'Machine learning models: Trained continuously on historical fraud data to identify new patterns and emerging fraud tactics.',
            'Manual review: A dedicated Trust & Safety team reviews flagged accounts and escalated reports daily.',
            'Community reporting: All users can report suspicious listings, bids, or accounts using the Report function.',
          ],
        },
      ],
    },
    {
      title: 'Verification Requirements',
      blocks: [
        {
          type: 'bullets',
          items: [
            'All sellers must complete full identity verification (government photo ID) before any listing can be created or go live.',
            'All buyers must complete full identity verification before placing bids in any auction.',
            'Payment methods (bank accounts, VNPAY accounts) must be registered to and verified against the account holder\'s verified identity.',
          ],
        },
      ],
    },
    {
      title: 'Consequences',
      blocks: [
        {
          type: 'callout',
          tone: 'rose',
          title: 'Confirmed fraud = permanent ban',
          body: 'Accounts confirmed to be engaged in fraud are permanently banned with no possibility of reinstatement. Criminal referrals are made where appropriate.',
        },
        {
          type: 'bullets',
          items: [
            'Fraudulent transactions are reversed wherever technically feasible; affected parties are notified within 3 business days.',
            'Cases involving criminal activity — including identity theft, payment fraud, and sale of counterfeit goods — are referred to Vietnamese law enforcement authorities under the Law on Cybersecurity (No. 24/2018/QH14).',
            'Chargeback fraud results in immediate account suspension and may be referred to the relevant payment network for investigation.',
          ],
        },
      ],
    },
    {
      title: 'How to Report Fraud',
      blocks: [
        {
          type: 'numbered',
          items: [
            'Use the Report button on the relevant listing, bid, or user profile.',
            'Alternatively, email support@bidnow.com with: your account email, the listing ID or username of the suspected bad actor, a clear description of the suspicious activity, and any supporting evidence: screenshots, message transcripts, photos.',
          ],
        },
        {
          type: 'callout',
          tone: 'indigo',
          title: 'Confidential reports',
          body: 'All reports are treated confidentially. Our Trust & Safety team investigates within 3 business days and may contact you for additional information.',
        },
      ],
    },
    {
      title: 'Protection Guarantees',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Buyer Protection: If a verified seller fails to deliver a paid-for item and our investigation confirms non-delivery, BidNow refunds the full purchase price to your BidNow Wallet within 7 business days.',
            "Deposit Escrow: All bid deposits are held in escrow by BidNow and released to sellers only upon confirmed delivery or buyer acceptance. Deposits are never released prematurely.",
            'Secure Payments: All transactions are processed through VNPAY with PCI-DSS compliance. BidNow never stores full card numbers.',
          ],
        },
      ],
    },
  ],
}

const related = [
  { tag: 'Policy', title: 'Community Guidelines', sub: 'Prohibited bidding practices and conduct', href: '/community-guidelines' },
  { tag: 'Security', title: 'Responsible Disclosure', sub: 'Report security vulnerabilities', href: '/security' },
  { tag: 'Policy', title: 'Seller Requirements', sub: 'Verification requirements for sellers', href: '/seller-requirements' },
]

export default function AntiFraudPage() {
  return <LegalPageTemplate doc={doc} related={related} />
}
