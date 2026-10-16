import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'

const FAQ_ITEMS = [
  {
    question: 'When does an auction actually end?',
    answer:
      'At its end time, unless an accepted bid arrives with less than two minutes left. Then five minutes are added to the end time. This can repeat with no cap, and every extension appears live for everyone watching.',
  },
  {
    question: 'What happens to my deposit?',
    answer:
      "It's locked automatically when you place your first bid on an auction. If you don't win, it goes back to your wallet as soon as the auction ends.",
  },
  {
    question: "Can I bid while I'm away?",
    answer:
      'Yes. Set a maximum bid and auto-bid will outbid rivals for you, up to your limit and no further.',
  },
  {
    question: 'What happens when I win?',
    answer:
      "You'll be notified straight away and have 48 hours to pay from your wallet. Your deposit counts toward the price. If payment isn't made in time, the deposit is forfeited and the result is cancelled.",
  },
  {
    question: 'How do I verify my account?',
    answer:
      'After registering, check your email for a one-time code (OTP). Enter it to activate your account.',
  },
  {
    question: 'Can I sell on BidNow?',
    answer:
      'Yes. Verified sellers can list items from the dashboard. Click "Start selling" to get started.',
  },
] as const

export function FAQAccordion() {
  return (
    <Accordion className="w-full border-t border-[var(--color-border-default)]" defaultValue={['faq-0']}>
      {FAQ_ITEMS.map((item, i) => (
        <AccordionItem key={item.question} value={`faq-${i}`} className="border-b border-[var(--color-border-default)] not-last:border-b">
          <AccordionTrigger className="items-center py-5 font-display text-[length:var(--font-size-lg)] font-medium hover:no-underline">
            {item.question}
          </AccordionTrigger>
          <AccordionContent className="max-w-3xl pr-10 text-[length:var(--font-size-base)] leading-relaxed text-muted-foreground">
            {item.answer}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  )
}
