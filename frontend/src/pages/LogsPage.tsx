import { useRef, useState, type ReactNode } from "react";
import { Download, ScrollText } from "lucide-react";
import { parseTime, useAdminRead } from "../api/adminLists";
import { downloadCsv } from "../api/http";
import {
  AUDIT_ACTIONS,
  AUDIT_TARGETS,
  SECURITY_EVENTS,
  LOG_CLEANUP_ADDRESS,
  logsAddress,
  type AuditPage,
  type AuditRow,
  type LogCleanup,
  type LogTab,
  type ScanPage,
  type ScanRow,
  type SecurityPage,
  type SecurityRow,
} from "../api/liveLogs";
import { Button, CellSub, Empty, EmptyState, FactList, InlineMessage, Note, PageHeader, Slip, SlipBody, SlipHeader, StatusBadge, TextLink, TimeText } from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { Pager } from "../components/Pager";
import { Tabs } from "../components/Tabs";
import { SelectBox, inputClass } from "../components/form";
import { useListParams } from "../lib/useListParams";
import { useToast } from "../components/Toast";
import { dateTimeInputValue, plainWord, plural } from "../lib/format";
import { cn } from "../lib/cn";

const TABS = [
  { id: "audit", label: "Audit log", note: "kept 3 years" },
  { id: "security", label: "Security log", note: "kept 90 days" },
  { id: "scans", label: "Picking scans", note: "kept 90 days" },
];

/** One filter: its label above, the control below (MASTER.md Section 12.5). */
function FilterField({ id, label, className, children }: { id: string; label: string; className?: string; children: ReactNode }) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-1", className)}>
      <label htmlFor={id} className="text-label font-semibold">
        {label}
      </label>
      {children}
    </div>
  );
}

function DateRange({ from, to, onChange }: { from: string; to: string; onChange: (patch: { from?: string; to?: string }) => void }) {
  return (
    <span className="grid w-full grid-cols-2 gap-2 lg:flex lg:w-auto">
      <FilterField id="logFrom" label="From">
        <input id="logFrom" name="from" type="datetime-local" value={dateTimeInputValue(from)} onChange={(e) => onChange({ from: e.target.value })} className={cn(inputClass, "w-full min-w-0 lg:w-[190px]")} />
      </FilterField>
      <FilterField id="logTo" label="To">
        <input id="logTo" name="to" type="datetime-local" value={dateTimeInputValue(to, true)} onChange={(e) => onChange({ to: e.target.value })} className={cn(inputClass, "w-full min-w-0 lg:w-[190px]")} />
      </FilterField>
    </span>
  );
}

function FilterText({ id, label, example, value, onChange, className }: { id: string; label: string; example: string; value: string; onChange: (value: string) => void; className?: string }) {
  return (
    <FilterField id={id} label={label} className={className}>
      <input id={id} name={label.toLowerCase().replace(/[^a-z0-9]+/g, "-")} placeholder={example} autoComplete="off" spellCheck={false} value={value} onChange={(e) => onChange(e.target.value)} className={inputClass} />
    </FilterField>
  );
}

function FilterBar({ label, children, onClear, active }: { label: string; children: ReactNode; onClear: () => void; active: boolean }) {
  return (
    <div role="search" aria-label={label} className="flex flex-wrap items-end gap-x-2 gap-y-3 border-b border-line px-4 py-3.5 md:px-5">
      {children}
      {active && (
        <Button level="quiet" small className="mb-[3px]" onClick={onClear}>
          Clear
        </Button>
      )}
    </div>
  );
}

function RawValue({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex min-w-0 gap-2">
      <span className="w-9 shrink-0 text-small font-semibold text-ink-muted">{label}</span>
      {value ? <code translate="no" className="min-w-0 font-mono text-[13px] leading-5 text-ink [overflow-wrap:anywhere]">{value}</code> : <Empty />}
    </div>
  );
}

const option = (values: readonly string[], all: string) => [{ value: "", label: all }, ...values.map((v) => ({ value: v, label: plainWord(v) }))];

export function LogsPage() {
  const toast = useToast();
  const [downloading, setDownloading] = useState(false);
  const defaults = {
    tab: "audit",
    from: "",
    to: "",
    actor: "",
    action: "",
    target: "",
    targetID: "",
    username: "",
    event: "",
    result: "",
    order: "",
    worker: "",
    page: "1",
  };
  const { values, set } = useListParams(defaults);
  const savedFilters = useRef<Partial<Record<LogTab, typeof defaults>>>({});
  const tab: LogTab = values.tab === "security" || values.tab === "scans" ? values.tab : "audit";
  const address = logsAddress(tab, values, Number(values.page) || 1);
  const audit = useAdminRead<AuditPage>(tab === "audit" ? address : null);
  const security = useAdminRead<SecurityPage>(tab === "security" ? address : null);
  const scans = useAdminRead<ScanPage>(tab === "scans" ? address : null);
  const cleanupRead = useAdminRead<LogCleanup>(LOG_CLEANUP_ADDRESS);
  const active = tab === "audit" ? audit : tab === "security" ? security : scans;

  const switchTab = (id: string) => {
    if (id === tab || (id !== "audit" && id !== "security" && id !== "scans")) return;
    savedFilters.current[tab] = values;
    set({ ...(savedFilters.current[id] ?? defaults), tab: id });
  };
  const clearFilters = () => set({ ...defaults, tab });
  const empty = active.loading ? <p className="p-4 text-small text-ink-muted" role="status">Loading log rows…</p> :
    active.error ? <div className="space-y-2 p-4"><InlineMessage kind="error">{active.error}</InlineMessage><Button small onClick={active.refresh}>Retry</Button></div> : (
    <EmptyState
      icon={<ScrollText aria-hidden="true" className="size-7" />}
      title="No log rows match these filters."
      action={
        <Button small level="quiet" onClick={clearFilters}>
          Clear filters
        </Button>
      }
    />
  );

  const auditColumns: Column<AuditRow>[] = [
    { key: "time", title: "Time", cell: (r) => <TimeText time={Date.parse(r.timestamp)} /> },
    { key: "actor", title: "Actor", mobile: "aside", cell: (r) => <span className="font-medium">{r.adminSearchAuditLogActorUsername ?? <Empty />}</span> },
    { key: "action", title: "Action", mobile: "title", className: "min-w-[180px]", cell: (r) => plainWord(r.action) },
    { key: "target", title: "Target", cell: (r) => [plainWord(r.targetType), r.targetID].filter(Boolean).join(" ") },
    {
      key: "values",
      title: "Old and new value",
      mobile: "wide",
      className: "min-w-[280px] max-w-[520px]",
      cell: (r) => (
        <div className="space-y-1">
          <RawValue label="Old" value={r.oldValue} />
          <RawValue label="New" value={r.newValue} />
        </div>
      ),
    },
  ];

  const securityColumns: Column<SecurityRow>[] = [
    { key: "time", title: "Time", cell: (r) => <TimeText time={Date.parse(r.timestamp)} /> },
    { key: "user", title: "User", mobile: "title", cell: (r) => <span className="font-semibold">{r.adminSearchSecurityLogUsername ?? <Empty />}</span> },
    { key: "event", title: "Event", className: "min-w-[170px]", cell: (r) => plainWord(r.eventType) },
    { key: "result", title: "Result", mobile: "aside", cell: (r) => <StatusBadge word={r.result} /> },
    { key: "ip", title: "IP address", cell: (r) => <span className="tabular">{r.ipAddress ?? <Empty />}</span> },
    {
      key: "details",
      title: "Details and device",
      mobile: "wide",
      className: "min-w-[260px] max-w-[460px]",
      cell: (r) => (
        <>
          <p>{r.details ?? <Empty />}</p>
          {r.deviceInfo && <CellSub className="break-all">{r.deviceInfo}</CellSub>}
        </>
      ),
    },
  ];

  const scanColumns: Column<ScanRow>[] = [
    { key: "time", title: "Time", cell: (r) => <TimeText time={Date.parse(r.scanTime)} /> },
    { key: "order", title: "Order", mobile: "title", cell: (r) => <TextLink to={`/admin/orders/${r.orderID}`}>{r.adminSearchPickingScanHistoryOrderReference ?? r.orderID}</TextLink> },
    { key: "worker", title: "Worker", cell: (r) => r.adminSearchPickingScanHistoryWorkerUsername ?? <Empty /> },
    { key: "code", title: "Scanned code", cell: (r) => <span className={cn("tabular", r.scannedIdentifier === "[unknown code]" && "text-ink-muted")}>{r.scannedIdentifier ?? <Empty />}</span> },
    { key: "product", title: "Product", cell: (r) => r.adminSearchPickingScanHistoryProductSKU ?? <Empty /> },
    { key: "result", title: "Result", mobile: "aside", cell: (r) => <StatusBadge word={r.result} /> },
    { key: "reason", title: "Reason", mobile: "wide", cell: (r) => r.reason || r.originalScanID != null ? (
      <>{r.reason && <p>{r.reason}</p>}{r.originalScanID != null && <CellSub>Reverses scan {r.originalScanID}</CellSub>}</>
    ) : <Empty /> },
  ];

  const download = async () => {
    setDownloading(true);
    try {
      await downloadCsv(logsAddress(tab, values));
      audit.refresh();
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "Could not download the log.");
    } finally {
      setDownloading(false);
    }
  };
  const auditPage = audit.data;
  const securityPage = security.data;
  const scanPage = scans.data;
  const rowCount = auditPage?.adminSearchAuditLogTotalRowCount ?? securityPage?.adminSearchSecurityLogTotalRowCount ?? scanPage?.adminSearchPickingScanHistoryTotalRowCount;
  const cleanup = cleanupRead.data;
  const cleanupRan = cleanup?.adminViewLogCleanupStatusHasRun;

  return (
    <>
      <PageHeader
        title="Logs"
        count={rowCount === undefined ? undefined : plural(rowCount, "row")}
        extra={<Note className="mt-2">Log rows cannot be changed or deleted here.</Note>}
        actions={
          <Button icon={<Download aria-hidden="true" className="size-4" />} onClick={download} busy={downloading} disabled={!active.data} busyText="Downloading…">
            Download CSV
          </Button>
        }
      />

      <Slip as="div">
        <div className="px-2 pt-1 md:px-3">
          <Tabs label="Logs" tabs={TABS} active={tab} onChange={switchTab} />
        </div>

        <div role="tabpanel" id="audit-panel" aria-labelledby="audit-tab" hidden={tab !== "audit"}>
          {tab === "audit" && (
            <>
              <FilterBar label="Filter the audit log" onClear={clearFilters} active={Boolean(values.from || values.to || values.actor || values.action || values.target || values.targetID)}>
                <DateRange from={values.from} to={values.to} onChange={set} />
                <FilterText id="logActor" label="Actor" example="e.g. admin_main" value={values.actor} onChange={(v) => set({ actor: v })} className="w-[calc(50%-4px)] lg:w-[150px]" />
                <FilterField id="logAction" label="Action" className="w-[calc(50%-4px)] lg:w-[230px]">
                  <SelectBox id="logAction" name="action" value={values.action} onChange={(v) => set({ action: v })} options={option(AUDIT_ACTIONS, "All actions")} />
                </FilterField>
                <FilterField id="logTarget" label="Target" className="w-[calc(50%-4px)] lg:w-[170px]">
                  <SelectBox id="logTarget" name="target" value={values.target} onChange={(v) => set({ target: v })} options={option(AUDIT_TARGETS, "All targets")} />
                </FilterField>
                <FilterText id="logTargetID" label="Target ID" example="e.g. 9" value={values.targetID} onChange={(v) => set({ targetID: v })} className="w-[calc(50%-4px)] lg:w-[110px]" />
              </FilterBar>
              <DataTable label="Audit log table" columns={auditColumns} rows={auditPage?.adminSearchAuditLogRowList ?? []} rowKey={(r) => r.auditID} minWidth="min-w-[940px]" empty={empty} />
              {auditPage && auditPage.adminSearchAuditLogTotalPageCount > 1 && <Pager label="Audit log pages" page={auditPage.adminSearchAuditLogPageNumber} pageCount={auditPage.adminSearchAuditLogTotalPageCount} onPage={(n) => set({ page: String(n) })} />}
            </>
          )}
        </div>

        <div role="tabpanel" id="security-panel" aria-labelledby="security-tab" hidden={tab !== "security"}>
          {tab === "security" && (
            <>
              <FilterBar label="Filter the security log" onClear={clearFilters} active={Boolean(values.from || values.to || values.username || values.event || values.result)}>
                <DateRange from={values.from} to={values.to} onChange={set} />
                <FilterText id="logUsername" label="Username" example="e.g. client_0451" value={values.username} onChange={(v) => set({ username: v })} className="w-full lg:w-[170px]" />
                <FilterField id="logEvent" label="Event" className="w-[calc(50%-4px)] lg:w-[220px]">
                  <SelectBox id="logEvent" name="event" value={values.event} onChange={(v) => set({ event: v })} options={option(SECURITY_EVENTS, "All events")} />
                </FilterField>
                <FilterField id="logResult" label="Result" className="w-[calc(50%-4px)] lg:w-[150px]">
                  <SelectBox id="logResult" name="result" value={values.result} onChange={(v) => set({ result: v })} options={option(["SUCCESS", "FAILURE", "REJECTED"], "All results")} />
                </FilterField>
              </FilterBar>
              <DataTable label="Security log table" columns={securityColumns} rows={securityPage?.adminSearchSecurityLogRowList ?? []} rowKey={(r) => r.securityLogID} minWidth="min-w-[940px]" empty={empty} />
              {securityPage && securityPage.adminSearchSecurityLogTotalPageCount > 1 && <Pager label="Security log pages" page={securityPage.adminSearchSecurityLogPageNumber} pageCount={securityPage.adminSearchSecurityLogTotalPageCount} onPage={(n) => set({ page: String(n) })} />}
            </>
          )}
        </div>

        <div role="tabpanel" id="scans-panel" aria-labelledby="scans-tab" hidden={tab !== "scans"}>
          {tab === "scans" && (
            <>
              <FilterBar label="Filter the picking scans" onClear={clearFilters} active={Boolean(values.from || values.to || values.order || values.worker || values.result)}>
                <DateRange from={values.from} to={values.to} onChange={set} />
                <FilterText id="logOrder" label="Order" example="e.g. ORD-10229" value={values.order} onChange={(v) => set({ order: v })} className="w-[calc(50%-4px)] lg:w-[150px]" />
                <FilterText id="logWorker" label="Worker" example="e.g. wh_picker_02" value={values.worker} onChange={(v) => set({ worker: v })} className="w-[calc(50%-4px)] lg:w-[160px]" />
                <FilterField id="logScanResult" label="Result" className="w-full lg:w-[150px]">
                  <SelectBox id="logScanResult" name="result" value={values.result} onChange={(v) => set({ result: v })} options={option(["ACCEPTED", "REJECTED", "REVERSED"], "All results")} />
                </FilterField>
              </FilterBar>
              <Note className="border-b border-line px-4 py-2.5 md:px-5">A scanned code is shown only when it is the SKU of a product. Anything else is shown as [unknown code].</Note>
              <DataTable label="Picking scans table" columns={scanColumns} rows={scanPage?.adminSearchPickingScanHistoryRowList ?? []} rowKey={(r) => r.scanID} minWidth="min-w-[900px]" empty={empty} />
              {scanPage && scanPage.adminSearchPickingScanHistoryTotalPageCount > 1 && <Pager label="Picking scan pages" page={scanPage.adminSearchPickingScanHistoryPageNumber} pageCount={scanPage.adminSearchPickingScanHistoryTotalPageCount} onPage={(n) => set({ page: String(n) })} />}
            </>
          )}
        </div>
      </Slip>

      <Slip labelledBy="cleanupTitle" className="mt-5">
        <SlipHeader title="Log clean-up" titleID="cleanupTitle" />
        <SlipBody>
          {cleanupRead.loading && <p role="status">Loading clean-up status…</p>}
          {cleanupRead.error && <><InlineMessage kind="error">{cleanupRead.error}</InlineMessage><Button small onClick={cleanupRead.refresh}>Retry</Button></>}
          {cleanup && <FactList
            columns={3}
            facts={[
              ["Last run", cleanupRan ? <TimeText key="t" time={parseTime(cleanup.adminViewLogCleanupStatusLastRunTime)} /> : "Not run yet"],
              ["Rows deleted", cleanupRan ? `Security log ${cleanup.adminViewLogCleanupStatusSecurityLogDeletedCount}, picking scans ${cleanup.adminViewLogCleanupStatusPickingScanHistoryDeletedCount}, audit log ${cleanup.adminViewLogCleanupStatusAuditLogDeletedCount}` : "-"],
              ["Keeping time", cleanup.adminViewLogCleanupStatusKeepRuleText],
            ]}
          />}
        </SlipBody>
      </Slip>
    </>
  );
}
