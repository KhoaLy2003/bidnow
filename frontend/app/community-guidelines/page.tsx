import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Community Guidelines',
  description: 'Rules of conduct for buyers and sellers on the BidNow platform.',
}

const doc: LegalDocument = {
  eyebrow: 'Policy · Community',
  title: 'Community Guidelines',
  intro:
    'Every policy in these guidelines exists to protect the integrity of auctions, the fairness of competition, and the safety of every person who uses this platform.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Enforced by', v: 'Trust & Safety' },
  ],
  sections: [
    {
      title: 'Our Standards',
      blocks: [
        {
          type: 'para',
          text: 'BidNow is a marketplace built entirely on trust. We hold ourselves — and every participant — to the same standard: honest listings, fair competition, and respectful interaction at all times.',
        },
      ],
    },
    {
      title: 'Prohibited Auction Items',
      blocks: [
        {
          type: 'callout',
          tone: 'rose',
          title: 'Zero tolerance',
          body: 'The following categories are never permitted. Listings will be removed immediately and accounts may be permanently banned.',
        },
        {
          type: 'bullets',
          items: [
            'Weapons, firearms, ammunition, and explosives',
            'Counterfeit, replica, or stolen goods of any kind',
            'Illegal drugs, controlled substances, or drug paraphernalia',
            'Items that infringe on intellectual property rights (unauthorized copies, bootlegs)',
            'Human remains, body parts, or protected wildlife and wildlife products',
            'Hazardous materials, chemicals, and radioactive materials',
            'Content or items prohibited under Vietnamese law, including Decree No. 59/2006/ND-CP and subsequent regulations governing prohibited goods',
            'Adult, sexually explicit, or obscene content',
          ],
        },
      ],
    },
    {
      title: 'Seller Conduct Rules',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Listings must accurately and completely describe the item, including all known defects, damage, and wear.',
            'All photos must be of the actual item being sold, taken by the seller. Stock images are not permitted.',
            'Sellers must honor all winning bids and complete transactions within their stated handling time.',
            'Setting an unreasonably high reserve price to gain listing visibility without genuine intent to sell is prohibited.',
            'Buyer contact information obtained through a transaction may only be used for purposes directly related to that transaction.',
          ],
        },
      ],
    },
    {
      title: 'Buyer Conduct Rules',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Bids are binding commitments. Do not place a bid on any item you cannot afford or do not genuinely intend to purchase if you win.',
            'Do not contact sellers to negotiate outside the platform for the purpose of avoiding fees.',
            'Treat sellers, other buyers, and BidNow staff with respect in all written and verbal communications.',
            'Do not file dispute claims that are false, exaggerated, or made in bad faith.',
          ],
        },
      ],
    },
    {
      title: 'Prohibited Bidding Practices',
      blocks: [
        {
          type: 'bullets',
          items: [
            'Shill bidding: Using alternate accounts or coordinating with associates to place artificial bids on your own listings to inflate the price.',
            'Bid shielding: Placing a very high bid to deter other bidders, then retracting it at the last moment to allow a confederate to win at a lower price.',
            'Bid siphoning: Contacting bidders on a listing to direct them to purchase the item outside the platform.',
            'Auction interference: Communicating with potential bidders to discourage them from participating in an auction.',
          ],
        },
      ],
    },
    {
      title: 'Feedback & Rating Guidelines',
      blocks: [
        {
          type: 'bullets',
          items: [
            'All feedback must be honest, factual, and based on the specific transaction being reviewed.',
            'Personal attacks, threats, harassment, or discriminatory language of any kind are strictly prohibited.',
            'Retaliatory feedback — feedback given solely in response to negative feedback received — is prohibited and will be removed.',
            'Do not include any personal information (phone numbers, home addresses, email addresses) in feedback.',
          ],
        },
      ],
    },
    {
      title: 'Consequences for Violations',
      blocks: [
        {
          type: 'table',
          headers: ['Severity', 'First Offense', 'Repeat Offense'],
          rows: [
            ['Minor (incomplete listing, missing photos)', 'Written warning', 'Listing removed'],
            ['Moderate (non-payment, inaccurate description)', '30-day suspension + warning', '12-month suspension'],
            ['Severe (shill bidding, prohibited items)', '12-month suspension', 'Permanent ban'],
            ['Criminal (fraud, counterfeit goods, identity theft)', 'Permanent ban', 'Permanent ban + referral to authorities'],
          ],
        },
        {
          type: 'para',
          text: 'BidNow reserves the right to escalate penalties at any severity level if circumstances warrant it.',
        },
      ],
    },
  ],
}

const related = [
  { tag: 'Policy', title: 'Anti-Fraud Policy', sub: 'Detection methods and fraud consequences', href: '/anti-fraud' },
  { tag: 'Policy', title: 'Seller Requirements', sub: 'Verification and performance standards', href: '/seller-requirements' },
  { tag: 'Legal', title: 'Terms of Service', sub: 'Full binding platform agreement', href: '/terms' },
]

export default function CommunityGuidelinesPage() {
  return <LegalPageTemplate doc={doc} related={related} />
}
