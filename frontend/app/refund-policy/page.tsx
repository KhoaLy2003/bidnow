import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Return & Refund Policy',
  description: 'Deposit refund rules, cancellation policy, and dispute resolution timelines.',
}

const doc: LegalDocument = {
  eyebrow: 'Legal · Policy',
  title: 'Return & Refund Policy',
  intro:
    'This policy defines when deposits are refunded, how cancellations work, and the timelines that apply after a transaction is complete.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Effective date', v: '2026-05-22' },
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Jurisdiction', v: 'Vietnam' },
  ],
  sections: [
    {
      title: 'Deposit Refund Rules',
      blocks: [
        {
          type: 'para',
          text: 'Bid deposits are required for auctions with a starting price above 10,000,000 VND. The deposit amount is specified on each auction listing before you register.',
        },
        {
          type: 'bullets',
          items: [
            'If you do not win the auction: your deposit is automatically refunded to your BidNow Wallet within 24 hours of auction close.',
            'If you win the auction: the deposit is applied toward the total purchase price; only the balance is due.',
            'If you withdraw from a registered auction before placing any bid: your deposit is refunded in full to your BidNow Wallet within 24 hours.',
            'Non-refundable cases: Deposits are forfeited if you are found to have engaged in deliberate bid manipulation, shill bidding, or any other fraudulent activity.',
          ],
        },
      ],
    },
    {
      title: 'Cancellation Policy',
      blocks: [
        {
          type: 'callout',
          tone: 'amber',
          title: 'Buyer cancellations are extremely limited',
          body: 'Once a bid is placed, it constitutes a binding commitment to purchase. Cancellation is only possible in documented cases of clear typographical error.',
        },
        {
          type: 'bullets',
          items: [
            'Seller cancellations: Sellers may cancel an auction before the first bid is placed without penalty. Cancellations after bidding has begun require BidNow approval and may result in seller penalties including suspension.',
            'Buyer cancellations: Requests must be submitted immediately via the Help Center and are evaluated within 24 hours. Not guaranteed.',
            'Platform-initiated cancellations: BidNow may cancel any auction that violates platform policies. Buyers affected receive a full refund of any deposits and payments within 24 hours.',
          ],
        },
      ],
    },
    {
      title: 'Non-Payment Consequences',
      blocks: [
        {
          type: 'para',
          text: 'If a winning bidder fails to complete payment within 48 hours of auction close:',
        },
        {
          type: 'numbered',
          items: [
            'The bid deposit is forfeited in full.',
            'A non-payment strike is recorded on the buyer\'s account.',
            'The auction item may be re-listed or offered to the next-highest qualified bidder.',
            'Three non-payment strikes within any 12-month rolling period result in permanent account suspension.',
          ],
        },
      ],
    },
    {
      title: 'Dispute Resolution Process',
      blocks: [
        {
          type: 'numbered',
          items: [
            'File a dispute within 7 calendar days of auction close through My Bids → Transaction History → File Dispute.',
            "BidNow's mediation team reviews evidence from both buyer and seller within 3 business days of the dispute being opened.",
            'If mediation does not produce an agreed resolution, the case is escalated to a senior resolution specialist within 5 additional business days.',
            "BidNow's final decision is binding per the Terms of Service.",
            'Disputes involving transaction amounts over 50,000,000 VND may be referred to the Vietnam E-Commerce and Digital Economy Agency (IDEA) for independent arbitration.',
          ],
        },
      ],
    },
    {
      title: 'Refund Timelines',
      blocks: [
        {
          type: 'table',
          headers: ['Scenario', 'Wallet credit', 'Bank transfer'],
          rows: [
            ['Auction lost — deposit refund', 'Within 24 hours of auction close', '1–3 business days after wallet credit'],
            ['Pre-bid withdrawal — deposit refund', 'Within 24 hours', '1–3 business days after wallet credit'],
            ['Platform-initiated cancellation', 'Within 24 hours', '1–3 business days after wallet credit'],
            ['Successful buyer dispute', 'Within 3–5 business days of decision', '1–3 business days after wallet credit'],
            ['Seller-initiated cancellation (post-bid)', 'Within 24 hours', '1–3 business days after wallet credit'],
          ],
        },
      ],
    },
  ],
}

const related = [
  { tag: 'Legal', title: 'Terms of Service', sub: 'Full bidding rules and payment obligations', href: '/terms' },
  { tag: 'Help', title: 'How It Works', sub: 'Step-by-step guide for buyers', href: '/how-it-works' },
  { tag: 'Policy', title: 'Community Guidelines', sub: 'Prohibited conduct and consequences', href: '/community-guidelines' },
]

export default function RefundPolicyPage() {
  return <LegalPageTemplate doc={doc} related={related} />
}
