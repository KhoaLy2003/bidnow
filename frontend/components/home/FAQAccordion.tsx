import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'

const FAQ_ITEMS = [
  {
    question: 'How does bidding work?',
    answer:
      'Place a bid above the current price. If no one outbids you before the timer ends, you win.',
  },
  {
    question: 'Is my payment information secure?',
    answer:
      'Yes. All transactions are encrypted and processed through verified payment providers.',
  },
  {
    question: 'What happens when I win an auction?',
    answer:
      "You'll receive an email with payment instructions. Complete payment within 48 hours to claim your item.",
  },
  {
    question: 'How do I verify my account?',
    answer:
      'After registering, check your email for a one-time code (OTP). Enter it to activate your account.',
  },
  {
    question: 'Can I sell on BidNow?',
    answer:
      'Yes. Verified sellers can list items from the dashboard. Click "Start Selling" to get started.',
  },
] as const

export function FAQAccordion() {
  return (
    <Accordion className="w-full">
      {FAQ_ITEMS.map((item, i) => (
        <AccordionItem key={i} value={`faq-${i}`}>
          <AccordionTrigger className="text-left">
            {item.question}
          </AccordionTrigger>
          <AccordionContent className="text-sm text-muted-foreground">
            {item.answer}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  )
}
