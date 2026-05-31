import type { Metadata } from 'next'
import { LegalPageTemplate } from '@/components/static/LegalPageTemplate'
import type { LegalDocument } from '@/components/static/LegalPageTemplate'

export const metadata: Metadata = {
  title: 'Seller Requirements',
  description: 'Verification, listing standards, and performance benchmarks for BidNow sellers.',
}

const doc: LegalDocument = {
  eyebrow: 'Policy · Sellers',
  title: 'Seller Requirements',
  intro:
    'These requirements apply to all sellers on BidNow. Meeting them protects buyers, maintains marketplace integrity, and qualifies you for full selling privileges.',
  version: '1.0',
  updated: '2026-05-22',
  contact: 'support@bidnow.com',
  meta: [
    { k: 'Last updated', v: '2026-05-22' },
    { k: 'Applies to', v: 'All sellers' },
    { k: 'Support', v: 'support@bidnow.com' },
  ],
  sections: [
    {
      title: 'Account Verification',
      blocks: [
        {
          type: 'para',
          text: 'Before creating any auction listing, all sellers must complete the following:',
        },
        {
          type: 'numbered',
          items: [
            'Email and phone verification: Both your email address and mobile phone number must be confirmed.',
            'Identity verification: Upload a valid government-issued photo ID (CCCD/CMND or passport). Verification is reviewed within 1–2 business days.',
            'Payment account: Connect a verified Vietnamese bank account or VNPAY account to receive sale proceeds. The account must be registered in your verified name.',
            'Business sellers: If selling as a registered business, submit your Business Registration Certificate (Giấy chứng nhận đăng ký doanh nghiệp) in addition to personal ID.',
          ],
        },
        {
          type: 'callout',
          tone: 'amber',
          title: 'Verification required before listing',
          body: 'Attempting to list items before verification is complete will result in listings being held in review.',
        },
      ],
    },
    {
      title: 'Listing Requirements',
      blocks: [
        {
          type: 'para',
          text: 'Every auction listing must include all of the following:',
        },
        {
          type: 'table',
          headers: ['Field', 'Requirement'],
          rows: [
            ['Title', 'Clear, accurate item name. Maximum 150 characters.'],
            ['Category', "Accurate category from BidNow's category list."],
            ['Condition', 'Honest condition rating: New / Like New / Good / Fair / Poor.'],
            ['Description', 'Complete description including all known defects, repairs, or modifications.'],
            ['Photos', 'Minimum 3 original photos of the actual item. Maximum 20. Minimum 800×600 px resolution.'],
            ['Starting Price', 'Minimum 10,000 VND.'],
            ['Auction Duration', 'Between 1 and 14 days.'],
            ['Shipping', 'Specified shipping methods, estimated costs, and handling time.'],
          ],
        },
        {
          type: 'callout',
          tone: 'rose',
          title: 'Non-compliant listings',
          body: 'Listings that do not meet these requirements may be rejected during review or removed after going live without prior notice.',
        },
      ],
    },
    {
      title: 'Performance Standards',
      blocks: [
        {
          type: 'para',
          text: 'Active sellers are expected to consistently meet the following benchmarks:',
        },
        {
          type: 'table',
          headers: ['Metric', 'Minimum Standard'],
          rows: [
            ['Transaction Completion Rate', '≥ 95% (no more than 5% cancelled after sale)'],
            ['On-Time Shipping Rate', '≥ 98% of items shipped within stated handling time'],
            ['Response Time to Buyers', '≤ 24 hours during business days'],
            ['Positive Feedback Rating', '≥ 90% of feedback ratings are positive'],
          ],
        },
        {
          type: 'callout',
          tone: 'amber',
          title: 'Performance Improvement Plan',
          body: 'Sellers who fall below these standards are placed on a Performance Improvement Plan. Listing privileges may be restricted during the improvement period.',
        },
      ],
    },
    {
      title: 'Prohibited Conduct',
      blocks: [
        {
          type: 'para',
          text: 'Sellers are strictly prohibited from:',
        },
        {
          type: 'bullets',
          items: [
            "Listing items they do not own or do not have the legal right to sell.",
            "Misrepresenting item condition, provenance, authenticity, age, or any material attribute.",
            "Cancelling confirmed transactions in order to sell the item to a different buyer or outside the platform.",
            "Offering the same item at lower prices on a different platform while an active BidNow auction is live.",
            "Communicating with buyers for the purpose of conducting transactions outside BidNow to avoid platform fees.",
            "Using manipulative tactics including shill bidding, reserve price abuse, or keyword stuffing in titles.",
          ],
        },
      ],
    },
    {
      title: 'Suspension and Removal Criteria',
      blocks: [
        {
          type: 'para',
          text: 'A seller account may be suspended or permanently removed from BidNow for any of the following:',
        },
        {
          type: 'bullets',
          items: [
            'Three or more policy violations within any rolling 12-month period.',
            'A Transaction Completion Rate falling below 80%.',
            'Confirmed fraud, shill bidding, listing of counterfeit goods, or item misrepresentation.',
            'A negative BidNow Wallet balance for more than 30 consecutive days.',
            'Failure to complete required identity verification upon request.',
            'A court order or directive from a Vietnamese government authority.',
            'Repeated failure to meet performance standards after a Performance Improvement Plan.',
          ],
        },
        {
          type: 'callout',
          tone: 'indigo',
          title: 'Appeals',
          body: 'Suspended accounts lose all active listings. Appeals may be submitted within 14 days of suspension notice to support@bidnow.com.',
        },
      ],
    },
  ],
}

const related = [
  { tag: 'Help', title: 'How It Works', sub: 'Step 6: selling your first item', href: '/how-it-works' },
  { tag: 'Policy', title: 'Community Guidelines', sub: 'Conduct rules for the full marketplace', href: '/community-guidelines' },
  { tag: 'Policy', title: 'Anti-Fraud Policy', sub: 'Prohibited bidding practices and consequences', href: '/anti-fraud' },
]

export default function SellerRequirementsPage() {
  return <LegalPageTemplate doc={doc} related={related} />
}
