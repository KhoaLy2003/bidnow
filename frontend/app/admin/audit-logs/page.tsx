"use client";

import React, { useEffect, useState } from "react";
import { useAuthStore } from "@/store/authStore";
import { adminService } from "@/services/adminService";
import {
  type AuditLogResponse,
  type AuditLogFilters,
  type AuditAction,
} from "@/types/admin";
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
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Search, RotateCcw, Eye, CalendarIcon } from "lucide-react";
import { toast } from "sonner";
import { formatDate } from "@/lib/utils";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

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

function formatDateForApi(
  date: Date | undefined,
  isToDate: boolean,
): string | undefined {
  if (!date) return undefined;
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  if (isToDate) {
    return `${year}-${month}-${day}T23:59:59`;
  }
  return `${year}-${month}-${day}T00:00:00`;
}

function DateRangePicker({
  fromDate,
  toDate,
  onChange,
}: {
  fromDate: Date | undefined;
  toDate: Date | undefined;
  onChange: (dates: { from?: Date; to?: Date }) => void;
}) {
  const [month, setMonth] = useState<Date>(fromDate || new Date());
  const [fromOpen, setFromOpen] = useState(false);
  const [toOpen, setToOpen] = useState(false);

  return (
    <div className="flex gap-2 w-full">
      <Popover open={fromOpen} onOpenChange={setFromOpen}>
        <PopoverTrigger
          className="flex-1"
          render={
            <Button
              variant="outline"
              className={cn(
                "w-full justify-start text-left font-normal",
                !fromDate && "text-muted-foreground",
              )}
            >
              <CalendarIcon className="mr-2 h-4 w-4" />
              {fromDate ? formatDate(fromDate.toISOString()) : "From"}
            </Button>
          }
        ></PopoverTrigger>
        <PopoverContent className="w-auto p-0">
          <Calendar
            mode="single"
            selected={fromDate}
            onSelect={(date) => {
              onChange({ from: date, to: toDate });
              if (date && date > (toDate || new Date(9999, 0, 0))) {
                onChange({ from: date, to: undefined });
              }
              setFromOpen(false);
            }}
            month={month}
            onMonthChange={setMonth}
          />
        </PopoverContent>
      </Popover>

      <Popover open={toOpen} onOpenChange={setToOpen}>
        <PopoverTrigger
          className="flex-1"
          render={
            <Button
              variant="outline"
              className={cn(
                "w-full justify-start text-left font-normal",
                !toDate && "text-muted-foreground",
              )}
            >
              <CalendarIcon className="mr-2 h-4 w-4" />
              {toDate ? formatDate(toDate.toISOString()) : "To"}
            </Button>
          }
        ></PopoverTrigger>
        <PopoverContent className="w-auto p-0">
          <Calendar
            mode="single"
            selected={toDate}
            onSelect={(date) => {
              onChange({ from: fromDate, to: date });
              setToOpen(false);
            }}
            month={month}
            onMonthChange={setMonth}
          />
        </PopoverContent>
      </Popover>
    </div>
  );
}

export default function AuditLogsPage() {
  const { accessToken } = useAuthStore();
  const [logs, setLogs] = useState<AuditLogResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filters, setFilters] = useState<AuditLogFilters>({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedLog, setSelectedLog] = useState<AuditLogResponse | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [calendarFrom, setCalendarFrom] = useState<Date | undefined>(undefined);
  const [calendarTo, setCalendarTo] = useState<Date | undefined>(undefined);

  const fetchLogs = async (currentPage = 0, apiFilters?: AuditLogFilters) => {
    if (!accessToken) return;

    setIsLoading(true);

    try {
      const response = await adminService.getAuditLogs(
        accessToken,
        apiFilters ?? buildApiFilters(filters, calendarFrom, calendarTo),
        currentPage,
      );

      setLogs(response.data);
      setTotalPages(response.pagination.totalPages);
      setPage(currentPage);
    } catch (error) {
      console.error("Failed to fetch audit logs", error);
      toast.error("Failed to load audit logs");
    } finally {
      setIsLoading(false);
    }
  };

  const buildApiFilters = (
    currentFilters: AuditLogFilters,
    from?: Date,
    to?: Date,
  ): AuditLogFilters => ({
    ...currentFilters,
    fromDate: from ? formatDateForApi(from, false) : undefined,
    toDate: to ? formatDateForApi(to, true) : undefined,
  });

  useEffect(() => {
    const timeout = setTimeout(() => fetchLogs(), 0);
    return () => clearTimeout(timeout);
  }, [accessToken]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();

    const apiFilters = buildApiFilters(filters, calendarFrom, calendarTo);

    fetchLogs(0, apiFilters);
  };

  const handleReset = () => {
    const emptyFilters: AuditLogFilters = {};

    setFilters(emptyFilters);
    setCalendarFrom(undefined);
    setCalendarTo(undefined);
    setPage(0);

    fetchLogs(0, buildApiFilters(emptyFilters));
  };

  const openDetails = (log: AuditLogResponse) => {
    setSelectedLog(log);
    setIsDetailsOpen(true);
  };

  const getActionBadge = (action: string) => {
    switch (action) {
      case "CREATE":
        return (
          <Badge className="bg-green-100 text-green-800 hover:bg-green-100 border-green-200">
            CREATE
          </Badge>
        );
      case "UPDATE":
        return (
          <Badge className="bg-blue-100 text-blue-800 hover:bg-blue-100 border-blue-200">
            UPDATE
          </Badge>
        );
      case "DELETE":
        return (
          <Badge className="bg-red-100 text-red-800 hover:bg-red-100 border-red-200">
            DELETE
          </Badge>
        );
      case "LOGIN":
        return (
          <Badge className="bg-purple-100 text-purple-800 hover:bg-purple-100 border-purple-200">
            LOGIN
          </Badge>
        );
      case "LOGOUT":
        return (
          <Badge className="bg-gray-100 text-gray-800 hover:bg-gray-100 border-gray-200">
            LOGOUT
          </Badge>
        );
      case "STATE_CHANGE":
        return (
          <Badge className="bg-yellow-100 text-yellow-800 hover:bg-yellow-100 border-yellow-200">
            STATE_CHANGE
          </Badge>
        );
      case "ADMIN_ACTION":
        return (
          <Badge className="bg-orange-100 text-orange-800 hover:bg-orange-100 border-orange-200">
            ADMIN
          </Badge>
        );
      default:
        return <Badge variant="secondary">{action}</Badge>;
    }
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
                    action:
                      value === "ALL" ? undefined : (value as AuditAction),
                  })
                }
              >
                <SelectTrigger className="w-full h-10  mb-0">
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

            {/* Date Range */}
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
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-10">
                      Loading audit logs...
                    </TableCell>
                  </TableRow>
                ) : logs.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-10">
                      No audit logs found.
                    </TableCell>
                  </TableRow>
                ) : (
                  logs.map((log) => (
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
                          <span className="font-medium text-xs">
                            {log.entityType}
                          </span>
                          <span className="text-[10px] text-muted-foreground truncate w-32">
                            {log.entityId}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="text-xs truncate w-40">
                            {log.actorEmail ||
                              (log.actorType === "SYSTEM"
                                ? "System"
                                : log.actorId)}
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
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-end space-x-2 py-4">
            <Button
              variant="outline"
              size="sm"
              onClick={() => fetchLogs(page - 1)}
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
              onClick={() => fetchLogs(page + 1)}
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
                  <p className="text-xs text-muted-foreground mb-1">
                    Correlation ID
                  </p>
                  <p className="font-mono text-xs break-all">
                    {selectedLog.correlationId || "N/A"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">
                    IP Address
                  </p>
                  <p className="text-xs">{selectedLog.ipAddress || "N/A"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">
                    User Agent
                  </p>
                  <p
                    className="text-xs break-all"
                    title={selectedLog.userAgent}
                  >
                    {selectedLog.userAgent || "N/A"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Reason</p>
                  <p className="text-xs">{selectedLog.reason || "N/A"}</p>
                </div>
              </div>

              <div>
                <h3 className="text-base font-medium mb-3 flex items-center gap-2">
                  Field Changes
                </h3>
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
