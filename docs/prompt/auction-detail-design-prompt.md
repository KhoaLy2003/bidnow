**Design Prompt:**

> Design a full auction detail page for a luxury online auction platform called **BidNow**. The visual style is Tesla-inspired: minimal, near-zero decoration, no shadows, no gradients, pure white backgrounds, single accent color Electric Indigo (`#4F46E5`) for primary actions only.
>
> **Layout (desktop):**
>
> - Full-width image gallery at the top: one large main image (`4:3` ratio, rounded corners) with a horizontal row of small square thumbnails (72×72px) below it. Active thumbnail gets an indigo ring.
> - Below the gallery: two-column grid — left column (~60%) has item details, right column (~360px) has a sticky bid panel card.
> - Left column from top to bottom: title + status badge on the same row, muted category/ID line, then three sections with uppercase muted labels: "About this item" (description text), "Specifications" (two-column label/value table with condition, category, item ID, starting price, reserve met ✓/✗), "Seller" (avatar + name + star rating + auction count inside a bordered card).
> - Full-width bid history section below both columns, separated by a hairline divider. Each row: avatar, bidder name (with bot icon for auto-bids, trophy for winning bid), relative timestamp, and monospace price right-aligned.
>
> **Bid panel — three states (show all three side by side or as separate screens):**
>
> 1. **Upcoming** (auction not started): Shows "Starting price" in large monospace, a pill/box with "Starts in" + large countdown timer (`HH:MM:SS`), a full-width outline button "Notify me when live" (fills indigo when toggled on), and "N watching" in muted text.
> 2. **Live** (auction in progress): Shows "Current bid" label + large monospace price + optional "You're winning" green badge or "You've been outbid" amber badge. Countdown timer below (turns amber when < 5 min, red when < 1 min). Muted wallet balance row ("Wallet balance: $2,400"). Bid input with `$`prefix,`+`and`−` steppers on the right, error message slot below. Full-width indigo "Place Bid" button. Separator. Auto-bid toggle row with a switch; when toggled on, a "Max auto-bid" input slides in. "N watching" footer.
> 3. **Ended — Won**: Green banner at top of panel (trophy icon + "🎉 You won this auction!" + "Final bid: $X,XXX"). Below: a white bordered card with "Congratulations! Next steps:" label, indigo "Proceed to Payment" button, outline "Contact Seller" button.
>
> 3b. **Ended — Lost**: Neutral muted banner (clock icon + "Auction ended" + "Final bid: $X,XXX"). Below: white bordered card with muted text "You were outbid." and outline "Browse similar items" button.
>
> **Typography:** Geist Sans for UI, DM Sans Display for the item title (large, weight 500), Geist Mono for all prices, bids, countdown numbers.
>
> **Colors:** White backgrounds, `#4F46E5` for primary buttons only, `#171A20` for headings, `#5C5E62` for muted labels. Status badge for "Active" = light blue bg + blue text, "Ending Soon" = amber, "Critical" = red, "Upcoming" = indigo/purple, "Won" = green, "Lost" = red muted.
>
> **Mobile:** Single column — gallery → bid panel → description → specs → seller → bid history. Bid panel appears above the details on mobile.
>
> Show desktop layout primary, include a mobile view for the live bidding state.
>
