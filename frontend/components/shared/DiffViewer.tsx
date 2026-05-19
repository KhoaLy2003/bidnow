"use client";

import React from "react";
import { type AuditLogDelta } from "@/types/admin";
import { Badge } from "@/components/ui/badge";
import { ArrowRight, FileText, Minus, Plus } from "lucide-react";

interface DiffViewerProps {
  delta: AuditLogDelta;
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "null";
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
}

function FieldDiff({ field, oldVal, newVal }: { field: string; oldVal: unknown; newVal: unknown }) {
  const oldStr = formatValue(oldVal);
  const newStr = formatValue(newVal);
  const isMultiline = oldStr.includes("\n") || newStr.includes("\n");
  const fieldName = field.replace(/([A-Z])/g, " $1").trim();

  return (
    <div className="rounded-lg border border-border/60 overflow-hidden">
      <div className="flex items-center gap-2 bg-muted/40 px-4 py-2.5 border-b border-border/60">
        <Badge variant="secondary" className="text-xs font-mono">
          {fieldName}
        </Badge>
      </div>

      {isMultiline ? (
        <div className="grid grid-cols-2 divide-x divide-border/60">
          <div className="bg-red-50/60 dark:bg-red-950/20">
            <div className="flex items-center gap-1.5 px-4 py-2 text-xs font-medium text-red-700 dark:text-red-400 border-b border-red-100 dark:border-red-900/40">
              <Minus className="size-3" />
              Before
            </div>
            <pre className="p-4 text-xs font-mono whitespace-pre-wrap break-all leading-relaxed text-red-900 dark:text-red-200">
              {oldStr}
            </pre>
          </div>
          <div className="bg-green-50/60 dark:bg-green-950/20">
            <div className="flex items-center gap-1.5 px-4 py-2 text-xs font-medium text-green-700 dark:text-green-400 border-b border-green-100 dark:border-green-900/40">
              <Plus className="size-3" />
              After
            </div>
            <pre className="p-4 text-xs font-mono whitespace-pre-wrap break-all leading-relaxed text-green-900 dark:text-green-200">
              {newStr}
            </pre>
          </div>
        </div>
      ) : (
        <div className="flex items-stretch">
          <div className="flex-1 bg-red-50/60 dark:bg-red-950/20 px-4 py-3 border-r border-border/60">
            <div className="flex items-center gap-1.5 text-[10px] uppercase font-semibold text-red-600 dark:text-red-400 mb-1">
              <Minus className="size-3" />
              Before
            </div>
            <p className="text-sm font-mono text-red-900 dark:text-red-200 break-all leading-relaxed">
              {oldStr}
            </p>
          </div>
          <div className="flex items-center justify-center px-3 bg-muted/30 text-muted-foreground">
            <ArrowRight className="size-4" />
          </div>
          <div className="flex-1 bg-green-50/60 dark:bg-green-950/20 px-4 py-3">
            <div className="flex items-center gap-1.5 text-[10px] uppercase font-semibold text-green-600 dark:text-green-400 mb-1">
              <Plus className="size-3" />
              After
            </div>
            <p className="text-sm font-mono text-green-900 dark:text-green-200 break-all leading-relaxed">
              {newStr}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

export const DiffViewer: React.FC<DiffViewerProps> = ({ delta }) => {
  const fields = Object.entries(delta);

  if (fields.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
        <FileText className="size-8 mb-2 opacity-40" />
        <p className="text-sm italic">No field changes recorded.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm text-muted-foreground mb-4">
        <Badge variant="outline" className="gap-1">
          {fields.length} field{fields.length !== 1 ? "s" : ""} changed
        </Badge>
      </div>
      {fields.map(([field, { old: oldVal, new: newVal }]) => (
        <FieldDiff key={field} field={field} oldVal={oldVal} newVal={newVal} />
      ))}
    </div>
  );
};
