import { cn } from "@/lib/utils";

interface BidNowMarkProps {
  size?: number;
  className?: string;
}

/** The BidNow "b" mark: a stem plus two stacked bids. Flat, painted with `currentColor` (defaults to the primary token). */
export function BidNowMark({ size = 24, className }: BidNowMarkProps) {
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={cn("text-primary", className)}
      xmlns="http://www.w3.org/2000/svg"
      fill="currentColor"
      aria-hidden="true"
    >
      <rect x="13" y="8" width="11" height="48" rx="3" />
      <path d="M31 8H37.5A10.5 10.5 0 0 1 37.5 29H31A3 3 0 0 1 28 26V11A3 3 0 0 1 31 8Z" />
      <path d="M31 33H39.5A11.5 11.5 0 0 1 39.5 56H31A3 3 0 0 1 28 53V36A3 3 0 0 1 31 33Z" />
    </svg>
  );
}
