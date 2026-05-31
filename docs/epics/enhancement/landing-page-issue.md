### **Core Sections (Must-Have)**

1. **Hero Section** — Strong value prop with CTA
   - "Bid on exclusive deals" or "Sell to collectors worldwide"
   - Animated auction countdown or featured item
   - Secondary CTA: "Browse Now" (for guests)

2. **Featured Auctions Grid** — 3-4 hot items
   - Shows social proof (bidding activity)
   - Creates FOMO with countdown timers
   - Real-time price updates build trust

3. **How It Works** — 3-step visual flow
   - Register → Bid/List → Pay & Receive
   - Reduces friction for new users
   - Address OTP verification in step 1

4. **Trust & Safety** — 4 cards: Verified Sellers, Secure Payments, Real-time Bidding, 24/7 Support
   - Critical for platform adoption
   - Addresses payment security concerns
   - Highlights real-time features

5. **Category Browse** — 5-6 top categories
   - Enable quick discovery
   - Link to filtered auction listings
   - Use category icons from your design system

6. **CTA Section** — "Ready to find great deals?"
   - Reinforce value
   - Primary button: "Sign Up Free"
   - Positioned after initial trust-building

### **Conversion & Engagement (Highly Recommended)**

7. **Social Proof / Stats** — 3 metrics
   - "50K+ Active Users"
   - "$2.5M Total Auction Value"
   - "4.8★ User Rating"
   - Numbers build credibility better than testimonials

### **Optional (For SEO & Engagement)**

8. **Latest Blog / Trending Items**
   - "How to Bid Smart" or "Trending Sneakers"
   - Improves SEO
   - Drives repeat visits
   - Can be replaced with FAQ in MVP

---

## Layout Recommendations

| Device | Layout | Key Consideration |
|---|---|---|
| **Mobile** | Single column stack | Hero, Categories, Featured Auctions, CTA, Stats, Footer |
| **Tablet** | 2-column for cards | Featured Grid 2x2, Category Browse horizontal scroll |
| **Desktop** | Full-width with max-width container | Featured Grid 4 cols, Featured Auctions carousel optional |

---

## Design System Alignment

Use your design tokens from `docs/design-system.md`:

- **Hero**: Brand indigo (`--color-brand-500`) CTA button with `--shadow-brand` hover
- **Featured Auctions**: Use `StatusBadge` component (ending-soon has red/orange accent with pulse animation)
- **Categories**: Icon buttons with semantic colors (info=blue for Electronics, success=green for Collectibles, warning=amber for Fashion)
- **Trust Cards**: Simple 2x2 grid with icon + text
- **Stats**: Metric cards with `--color-background-secondary` and `--font-size-lg` for numbers

---

## Content Priority by User Segment

| User Type | Primary Sections | Secondary |
|---|---|---|
| **Guest (Browsing)** | Hero → Featured Auctions → Categories | How It Works, Trust & Safety |
| **New Visitor (Mobile)** | Hero → CTA → Stats | Featured Auctions (scroll fatigue) |
| **Returning User (Logged In)** | Skip Hero, show personalized "For You" auctions | Recent searches, watchlist |