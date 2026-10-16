**Design Prompt:**

> Design the **My Bids** page for a luxury online auction platform called **BidNow**. It lists the *active* auctions the signed-in user is currently bidding on. The visual style is Tesla-inspired: minimal, near-zero decoration, no shadows, no gradients, pure white backgrounds, single accent color Electric Indigo (`#4F46E5`) for primary actions only.
>
> **Page header:** `My Bids` in DM Sans Display (large, weight 500) with a muted one-line subtitle ("Active auctions you've bid on"). Below it, a tab bar: **All · Winning · Outbid**, each with a small count (e.g. `Outbid 2`). Active tab has an indigo underline. A quiet "Ending soonest" sort label sits on the right.
>
> **Desktop (primary):** a bordered list, one row per auction, hairline dividers. Each row, left to right:
>
> - 72×72 rounded image thumbnail.
> - Title (weight 500, one line, truncated) with a muted "N bids" line beneath.
> - Status pill: **Winning** (green bg + green text) or **Outbid** (amber bg + amber text).
> - Two stacked monospace figures with 10px uppercase muted labels: "Your bid" and "Current bid". When outbid, the current bid is emphasised.
> - Time left in Geist Mono (`HH:MM:SS`, or `Xd Yh` when over a day). Turns amber under 5 minutes and red under 1 minute.
> - Right-aligned action: outbid rows get an indigo **Bid again** button; winning rows get a quiet outline **View** button.
>
> Outbid rows sort above winning rows only when they end within the same hour; otherwise ordering is purely by time left. Show a mix: three winning, two outbid, one ending in under 5 minutes, one in under 1 minute.
>
> **Mobile (375px):** single column of cards (image left, title and status right, then a two-column "Your bid / Current bid" strip, then the countdown and a full-width action). Tabs become a horizontally scrollable pill row. Respect the floating bottom nav dock (add bottom padding).
>
> **States to show as separate artboards:**
>
> 1. Populated (desktop and mobile).
> 2. Loading: skeleton rows matching the row layout.
> 3. Empty (no active bids): centred icon, "You're not bidding on anything yet", and an indigo **Browse auctions** button. Also a variant for an empty Outbid tab ("Nothing to catch up on — you're winning everything").
> 4. Error: an inline banner above the list ("Couldn't refresh your bids") with a **Retry** text button; the last loaded rows stay visible beneath it.
>
> **Typography:** Geist Sans for UI, DM Sans Display for the page title, Geist Mono for all prices, bids and countdown numbers.
>
> **Colors:** White backgrounds, `#4F46E5` for primary buttons only, `#171A20` for headings, `#5C5E62` for muted labels, hairline borders `#E5E7EB`. Provide a dark-mode variant of the populated desktop view.
