import { useCallback, useState } from "react";
import { ClipboardList, Download, Search } from "lucide-react";
import { loadOrders, orderSearchPath, type LiveOrderRow } from "../api/liveOrders";
import { downloadCsv } from "../api/http";
import { useWorkQuery } from "../api/liveWork";
import { WorkQueryNotice } from "../components/WorkQueryNotice";
import { useToast } from "../components/Toast";
import { Button, CellMain, Empty, EmptyState, Flag, PageHeader, Slip, StatusBadge, TextLink, TimeText } from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { Pager } from "../components/Pager";
import { SelectBox, inputClass } from "../components/form";
import { useListParams } from "../lib/useListParams";
import { dateTimeInputValue, formatMoney, plainWord, plural } from "../lib/format";
import { ORDER_STATUSES } from "../lib/status";
import { cn } from "../lib/cn";

const STATUS_OPTIONS = [{ value: "", label: "All statuses" }, ...ORDER_STATUSES.map((s) => ({ value: s, label: plainWord(s) }))];

export function OrdersPage() {
  const { values, set, clear } = useListParams({ q: "", status: "", from: "", to: "", page: "1" });
  const { q, status, from, to } = values;
  const pageNumber = Number(values.page) || 1;
  const result = useWorkQuery(useCallback(() => loadOrders({ keyword: q, status, from, to }, pageNumber), [q, status, from, to, pageNumber]));
  const { data: page } = result;
  const [downloading, setDownloading] = useState(false);
  const toast = useToast();
  const filtered = Boolean(values.q || values.status || values.from || values.to);

  const download = async () => {
    setDownloading(true);
    try {
      await downloadCsv(orderSearchPath({ keyword: q, status, from, to }), "orders.csv");
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "The download failed.");
    } finally {
      setDownloading(false);
    }
  };

  const columns: Column<LiveOrderRow>[] = [
    {
      key: "order",
      title: "Order",
      mobile: "title",
      cell: (r) => (
        <CellMain>
          <TextLink to={`/admin/orders/${r.order.orderID}`}>{r.order.orderReference}</TextLink>
          {r.hasOpenCase && <Flag family="turn">Open case</Flag>}
        </CellMain>
      ),
    },
    { key: "client", title: "Client", cell: (r) => r.client },
    { key: "created", title: "Created", cell: (r) => <TimeText time={r.order.createdTime} /> },
    { key: "total", title: "Total", numeric: true, cell: (r) => formatMoney(r.order.totalAmount) },
    { key: "status", title: "Order status", mobile: "aside", cell: (r) => <StatusBadge word={r.order.orderStatus} /> },
    { key: "payment", title: "Payment", cell: (r) => (r.paymentStatus ? <StatusBadge word={r.paymentStatus} /> : <Empty />) },
    { key: "refund", title: "Refund", cell: (r) => (r.refundStatus ? <StatusBadge word={r.refundStatus} /> : <Empty />) },
  ];

  return (
    <>
      <PageHeader
        title="Orders"
        count={page ? plural(page.total, "order") : undefined}
        actions={
          <Button icon={<Download aria-hidden="true" className="size-4" />} onClick={download} busy={downloading} disabled={!page || result.pending}>
            Download CSV
          </Button>
        }
      />
      <Slip as="div">
        <div role="search" aria-label="Filter orders" className="flex flex-wrap items-end gap-2 border-b border-line px-4 py-3 md:px-5">
          <span className="relative w-full sm:w-[260px]">
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
            <input type="search" name="q" autoComplete="off" spellCheck={false} aria-label="Search order or client" placeholder="Search order or client…" value={values.q} onChange={(e) => set({ q: e.target.value })} className={cn(inputClass, "pl-9")} />
          </span>
          <SelectBox aria-label="Order status" name="status" value={values.status} onChange={(v) => set({ status: v })} options={STATUS_OPTIONS} className="w-full sm:w-[210px]" />
          <span className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto sm:items-center">
            <label className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-center sm:gap-2">
              <span className="text-label font-semibold">From</span>
              <input type="datetime-local" name="from" value={dateTimeInputValue(values.from)} onChange={(e) => set({ from: e.target.value })} className={cn(inputClass, "w-full min-w-0 sm:w-[190px]")} />
            </label>
            <label className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-center sm:gap-2">
              <span className="text-label font-semibold">To</span>
              <input type="datetime-local" name="to" value={dateTimeInputValue(values.to, true)} onChange={(e) => set({ to: e.target.value })} className={cn(inputClass, "w-full min-w-0 sm:w-[190px]")} />
            </label>
          </span>
          {filtered && (
            <Button level="quiet" small onClick={clear}>
              Clear
            </Button>
          )}
        </div>
        <WorkQueryNotice {...result} />
        {page && <DataTable
          label="Orders table"
          columns={columns}
          rows={page.rows}
          rowKey={(r) => r.order.orderID}
          minWidth="min-w-[860px]"
          empty={
            <EmptyState
              icon={<ClipboardList aria-hidden="true" className="size-7" />}
              title="No orders match these filters."
              action={
                <Button small level="quiet" onClick={clear}>
                  Clear filters
                </Button>
              }
            />
          }
        />}
        {page && page.total > 0 && <Pager label="Orders pages" page={page.page} pageCount={page.pageCount} onPage={(n) => set({ page: String(n) })} />}
      </Slip>
    </>
  );
}
