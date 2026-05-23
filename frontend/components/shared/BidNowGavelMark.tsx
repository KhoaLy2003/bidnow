import { cn } from "@/lib/utils";

interface BidNowGavelMarkProps {
  size?: number;
  className?: string;
}

export function BidNowGavelMark({ size = 24, className }: BidNowGavelMarkProps) {
  return (
    <svg
      viewBox="0 0 80 80"
      width={size}
      height={size}
      className={cn(className)}
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="bnl-bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#6366F1" />
          <stop offset="100%" stopColor="#3730A3" />
        </linearGradient>
        <linearGradient id="bnl-sheen" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="white" stopOpacity="0.10" />
          <stop offset="100%" stopColor="white" stopOpacity="0" />
        </linearGradient>
      </defs>
      <rect width="80" height="80" rx="20" fill="url(#bnl-bg)" />
      <rect width="80" height="40" rx="20" fill="url(#bnl-sheen)" />
      <rect width="80" height="20" y="20" fill="url(#bnl-sheen)" />
      <line x1="70" y1="44" x2="32" y2="8" stroke="white" strokeWidth="17" strokeLinecap="round" />
      <line x1="47" y1="28" x2="16" y2="62" stroke="white" strokeWidth="10" strokeLinecap="round" />
      <rect x="12" y="66" width="50" height="7" rx="3.5" fill="white" fillOpacity="0.22" />
      <rect x="16" y="66" width="42" height="2" rx="1" fill="white" fillOpacity="0.12" />
    </svg>
  );
}
