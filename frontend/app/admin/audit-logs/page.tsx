"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { adminService } from "@/services/adminService";
import {
  type AuditLogResponse,
  type AuditLogFilters,
  type AuditAction,
} from "@/types/api/admin.api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { DiffViewer } from "@/components/shared/DiffViewer";
import { DateRangePicker } from "@/components/shared/DateRangePicker";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Search, RotateCcw, Eye } from "lucide-react";
import { toast } from "sonner";
import { formatDate, getErrorMessage } from "@/lib/utils";
import { formatStartOfDay, formatEndOfDay } from "@/lib/format";
import { Label } from "@/components/ui/label";

const AUDIT_ACTIONS: { value: AuditAction | "ALL"; label: string }[] = [
  { value: "ALL", label: "All Actions" },
  { value: "CREATE", label: "CREATE" },
  { value: "UPDATE", label: "UPDATE" },
  { value: "DELETE", label: "DELETE" },
  { value: "STATE_CHANGE", label: "STATE_CHANGE" },
  { value: "LOGIN", label: "LOGIN" },
  { value: "LOGOUT", label: "LOGOUT" },
  { value: "ADMIN_ACTION", label: "ADMIN_ACTION" },
];

const ACTION_BADGE_CLASS: Record<string, string> = {
  CREATE:       "bg-[var(--color-auction-active-bg)] text-[var(--color-auction-active-text)] border-[var(--color-auction-active-border)]",
  UPDATE:       "bg-secondary text-secondary-foreground",
  DELETE:       "bg-destructive/10 text-destructive border-destructive/20",
  LOGIN:        "bg-[var(--color-auction-won-bg)] text-[var(--color-auction-won-text)] border-[var(--color-auction-won-border)]",
  LOGOUT:       "bg-muted text-muted-foreground",
  STATE_CHANGE: "bg-[var(--color-auction-ending-bg)] text-[var(--color-auction-ending-text)] border-[var(--color-auction-ending-border)]",
  ADMIN_ACTION: "bg-[var(--color-auction-critical-bg)] text-[var(--color-auction-critical-text)] border-[var(--color-auction-critical-border)]",
}

function getActionBadge(action: string) {
  const cls = ACTION_BADGE_CLASS[action]
  if (cls) return <Badge className={cls}>{action === "ADMIN_ACTION" ? "ADMIN" : action}</Badge>
  return <Badge variant="secondary">{action}</Badge>
}

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLogResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filters, setFilters] = useState<AuditLogFilters>({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedLog, setSelectedLog] = useState<AuditLogResponse | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [calendarFrom, setCalendarFrom] = useState<Date | undefined>(undefined);
  const [calendarTo, setCalendarTo] = useState<Date | undefined>(undefined);

  const stateRef = useRef({ filters, calendarFrom, calendarTo });
  useEffect(() => {
    stateRef.current = { filters, calendarFrom, calendarTo };
  });

  const fetchLogs = useCallback((currentPage = 0, apiFilters?: AuditLogFilters) => {
    const { filters: f, calendarFrom: from, calendarTo: to } = stateRef.current;
    adminService
      .getAuditLogs(
        apiFilters ?? {
          ...f,
          fromDate: from ? formatStartOfDay(from) : undefined,
          toDate: to ? formatEndOfDay(to) : undefined,
        },
        currentPage,
      )
      .then((response) => {
        setLogs(response.data);
        setTotalPages(response.pagination.totalPages);
        setPage(currentPage);
      })
      .catch((error) => {
        toast.error(getErrorMessage(error, "Failed to load audit logs"));
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const { filters: f, calendarFrom: from, calendarTo: to } = stateRef.current;
    setIsLoading(true);
    fetchLogs(0, {
      ...f,
      fromDate: from ? formatStartOfDay(from) : undefined,
      toDate: to ? formatEndOfDay(to) : undefined,
    });
  };

  const handleReset = () => {
    setFilters({});
    setCalendarFrom(undefined);
    setCalendarTo(undefined);
    setPage(0);
    setIsLoading(true);
    fetchLogs(0, {});
  };

  const openDetails = (log: AuditLogResponse) => {
    setSelectedLog(log);
    setIsDetailsOpen(true);
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-medium">Audit Logs</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={handleSearch}
            className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-end"
          >
            <div className="lg:col-span-3 space-y-2">
              <Label htmlFor="actorEmail">Actor Email</Label>
              <Input
                id="actorEmail"
                placeholder="Enter actor email..."
                value={filters.actorEmail || ""}
                onChange={(e) =>
                  setFilters({ ...filters, actorEmail: e.target.value })
                }
              />
            </div>

            <div className="lg:col-span-2 space-y-2">
              <Label>Action</Label>
              <Select
                value={filters.action || "ALL"}
                onValueChange={(value) =>
                  setFilters({
                    ...filters,
                    action: value === "ALL" ? undefined : (value as AuditAction),
                  })
                }
              >
                <SelectTrigger className="w-full h-10 mb-0">
                  <SelectValue placeholder="All Actions" />
                </SelectTrigger>
                <SelectContent>
                  {AUDIT_ACTIONS.map((a) => (
                    <SelectItem key={a.value} value={a.value}>
                      {a.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="lg:col-span-5 space-y-2">
              <Label>Date Range</Label>
              <DateRangePicker
                fromDate={calendarFrom}
                toDate={calendarTo}
                onChange={({ from, to }) => {
                  setCalendarFrom(from);
                  setCalendarTo(to);
                }}
              />
            </div>

            <div className="lg:col-span-2 flex gap-2">
              <Button type="submit" className="flex-1">
                <Search className="h-4 w-4 mr-2" />
                Search
              </Button>
              <Button
                type="button"
                variant="outline"
                size="icon"
                onClick={handleReset}
                className="shrink-0"
              >
                <RotateCcw className="h-4 w-4" />
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Timestamp</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Entity</TableHead>
                  <TableHead>Actor</TableHead>
                  <TableHead className="text-right">Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading && (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-10">
                      Loading audit logs...
                    </TableCell>
                  </TableRow>
                )}
                {!isLoading && logs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-10">
                      No audit logs found.
                    </TableCell>
                  </TableRow>
                )}
                {!isLoading && logs.map((log) => (
                  <TableRow
                    key={log.id}
                    role="button"
                    tabIndex={0}
                    className="cursor-pointer"
                    onClick={() => openDetails(log)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        openDetails(log);
                      }
                    }}
                  >
                    <TableCell className="text-xs">
                      {log.timestamp ? formatDate(log.timestamp) : "N/A"}
                    </TableCell>
                    <TableCell>{getActionBadge(log.action)}</TableCell>
                    <TableCell>
                      <div className="flex flex-col">
                        <span className="font-medium text-xs">{log.entityType}</span>
                        <span className="text-[10px] text-muted-foreground truncate w-32">
                          {log.entityId}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col">
                        <span className="text-xs truncate w-40">
                          {log.actorEmail ||
                            (log.actorType === "SYSTEM" ? "System" : log.actorId)}
                        </span>
                        <span className="text-[10px] text-muted-foreground uppercase">
                          {log.actorType}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell
                      className="text-right"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => openDetails(log)}
                      >
                        <Eye className="h-4 w-4 mr-2" /> View Diff
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-end space-x-2 py-4">
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setIsLoading(true);
                fetchLogs(page - 1);
              }}
              disabled={page === 0 || isLoading}
            >
              Previous
            </Button>
            <div className="text-sm font-medium">
              Page {page + 1} of {totalPages || 1}
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setIsLoading(true);
                fetchLogs(page + 1);
              }}
              disabled={page + 1 >= totalPages || isLoading}
            >
              Next
            </Button>
          </div>
        </CardContent>
      </Card>

      <Dialog open={isDetailsOpen} onOpenChange={setIsDetailsOpen}>
        <DialogContent className="max-w-[95vw] w-full max-h-[95vh] h-[95vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle>Audit Log Details</DialogTitle>
          </DialogHeader>
          {selectedLog && (
            <div className="space-y-6 overflow-y-auto flex-1 pr-2">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 bg-muted/30 p-4 rounded-lg text-sm">
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Correlation ID</p>
                  <p className="font-mono text-xs break-all">
                    {selectedLog.correlationId || "N/A"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">IP Address</p>
                  <p className="text-xs">{selectedLog.ipAddress || "N/A"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">User Agent</p>
                  <p className="text-xs break-all" title={selectedLog.userAgent}>
                    {selectedLog.userAgent || "N/A"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Reason</p>
                  <p className="text-xs">{selectedLog.reason || "N/A"}</p>
                </div>
              </div>

              <div>
                <h3 className="text-base font-medium mb-3">Field Changes</h3>
                {selectedLog.delta ? (
                  <DiffViewer delta={selectedLog.delta} />
                ) : (
                  <p className="text-sm text-muted-foreground italic">
                    No changes recorded in delta.
                  </p>
                )}
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
