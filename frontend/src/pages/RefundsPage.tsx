import { useCallback, useRef, useState } from "react";
import { Download } from "lucide-react";
import { downloadCsv } from "../api/http";
import { apiTime, useWorkQuery, type DecidedRow } from "../api/liveWork";
import { checkProviderStatus, findRefund, loadRefunds, refundsPath, retryRefund, type LiveRefundRow as RefundRow } from "../api/liveCases";
import { Pager } from "../components/Pager";
import { WorkQueryNotice } from "../components/WorkQueryNotice";
import { AllClear, Button, CellMain, CellSub, Empty, Note, PageHeader, Slip, SlipHeader, SlipStrip, StatusBadge, TextLink, TimeText } from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { ConfirmDialog, afterClose } from "../components/ConfirmDialog";
import { CheckboxField, ReasonField, reasonReady } from "../components/form";
import { useToast } from "../components/Toast";
import { useListParams } from "../lib/useListParams";
import { formatAge, formatMoney, plainWord } from "../lib/format";

const countUnresolved = async () => (await loadRefunds(true, 1)).total;

export function RefundsPage() {
  const toast = useToast();
  const { values, set } = useListParams({ unresolved: "true", page: "1" });
  const unresolvedOnly = values.unresolved !== "false";
  const [keptRows, setKeptRows] = useState<Map<number, RefundRow>>(new Map());
  const [decisions, setDecisions] = useState<Map<number, DecidedRow>>(new Map());
  const [downloading, setDownloading] = useState(false);
  const [retry, setRetry] = useState<RefundRow | null>(null);
  const [reason, setReason] = useState("");
  const [checking, setChecking] = useState<number | null>(null);
  const focusAfter = useRef<string | null>(null);

  const page = Number(values.page) || 1;
  const result = useWorkQuery(useCallback(() => loadRefunds(unresolvedOnly, page), [unresolvedOnly, page]));
  const counts = useWorkQuery(countUnresolved);
  const rows = result.data?.rows.map((r) => keptRows.get(r.refund.refundID) ?? r) ?? [];
  const unresolvedCount = counts.data;

  const changeFilter = (checked: boolean) => {
    setKeptRows(new Map());
    setDecisions(new Map());
    set({ unresolved: String(checked) });
  };

  const updateRow = async (r: RefundRow) => {
    try {
      const updated = await findRefund(r.refund.refundID);
      if (updated) setKeptRows((map) => new Map(map).set(r.refund.refundID, updated));
    } catch {
      toast.show("information", "Reload the refunds page to read the latest refund status.");
    }
    counts.refresh();
  };

  const download = async () => {
    setDownloading(true);
    try {
      await downloadCsv(refundsPath(unresolvedOnly), "refunds.csv");
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "The download failed.");
    } finally {
      setDownloading(false);
    }
  };

  const columns: Column<RefundRow>[] = [
    {
      key: "order",
      title: "Order",
      mobile: "title",
      cell: (r) => (
        <>
          <CellMain>
            <TextLink to={`/admin/orders/${r.order.orderID}`}>{r.order.orderReference}</TextLink>
          </CellMain>
          <CellSub className="font-normal">
            Refund {r.refund.refundID}, order {plainWord(r.order.orderStatus).toLowerCase()}
          </CellSub>
        </>
      ),
    },
    { key: "amount", title: "Amount", numeric: true, mobile: "aside", cell: (r) => <span className="font-semibold">{formatMoney(r.refund.amount)}</span> },
    {
      key: "status",
      title: "Status",
      cell: (r) => {
        const decision = decisions.get(r.refund.refundID);
        return (
          <div data-decided-cell>
            <StatusBadge word={decision?.adminDecidedRowStateWord ?? r.refund.refundStatus} />
            {decision && (
              <CellSub>
                Refund {decision.refundID} requested by {decision.adminDecidedRowDecidedByUsername}, <TimeText time={apiTime(decision.adminDecidedRowDecidedTime)} />
              </CellSub>
            )}
            {!decision && r.refund.replacedByRefundID && <CellSub>Replaced by refund {r.refund.replacedByRefundID}</CellSub>}
          </div>
        );
      },
    },
    {
      key: "age",
      title: "Age",
      cell: (r) => (
        <>
          <p>{r.refund.refundStatus === "REFUNDED" && r.refund.completedTime ? "Finished" : formatAge(r.refund.createdTime, r.refund.createdTime + r.refund.ageHours * 3600000)}</p>
          <CellSub>Since <TimeText time={r.refund.createdTime} /></CellSub>
        </>
      ),
    },
    {
      key: "check",
      title: "Last check",
      cell: (r) =>
        r.refund.lastCheckTime ? (
          <>
            <TimeText time={r.refund.lastCheckTime} />
            <CellSub>Provider answer: {plainWord(r.refund.lastProviderAnswer ?? "UNKNOWN").toLowerCase()}</CellSub>
          </>
        ) : (
          <span className="text-ink-muted">Not checked yet</span>
        ),
    },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      cell: (r) => {
        if (!r.isUnresolved || decisions.has(r.refund.refundID)) return <Empty />;
        const ref = r.order.orderReference;
        return (
          <div className="flex flex-wrap gap-2">
            <Button
              small
              busy={checking === r.refund.refundID}
              busyText="Asking…"
              disabled={checking !== null}
              aria-label={`Check provider status for order ${ref}`}
              onClick={async () => {
                setChecking(r.refund.refundID);
                const answer = await checkProviderStatus(r.refund.refundID);
                if (answer.ok && answer.data) {
                  const check = answer.data;
                  setKeptRows((map) => new Map(map).set(r.refund.refundID, {
                    ...r,
                    isUnresolved: check.refundStatus !== "REFUNDED" && !r.refund.replacedByRefundID,
                    refund: { ...r.refund, refundStatus: check.refundStatus, lastProviderAnswer: check.adminCheckRefundStatusProviderOutcome },
                  }));
                  await updateRow(r);
                }
                setChecking(null);
                toast.show(answer.ok ? "information" : "error", answer.message);
              }}
            >
              Check provider status
            </Button>
            {r.refund.refundStatus === "FAILED" && (
              <Button
                small
                aria-label={`Retry refund for order ${ref}`}
                disabled={checking !== null}
                onClick={() => {
                  focusAfter.current = null;
                  setReason("");
                  setRetry(r);
                }}
              >
                Retry refund
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader title="Refunds" count={unresolvedCount === undefined ? undefined : unresolvedCount === 0 ? "Every refund is finished." : `${unresolvedCount} not finished`} />
      <Slip labelledBy="refundsTitle">
        <SlipHeader
          title="Refunds"
          titleID="refundsTitle"
          count={result.data?.total}
          controls={
            <>
              <CheckboxField label="Unresolved only" checked={unresolvedOnly} onChange={changeFilter} />
              <Button small icon={<Download aria-hidden="true" className="size-4" />} onClick={download} busy={downloading} disabled={!result.data || result.pending}>
                Download CSV
              </Button>
            </>
          }
        />
        <SlipStrip>
          <Note>A refund is shown as Refunded only after the payment provider confirmed it. It cannot be set by hand.</Note>
        </SlipStrip>
        <WorkQueryNotice {...result} />
        {result.data && <DataTable
          cardsUntil="xl"
          label="Refunds table"
          columns={columns}
          rows={rows}
          rowKey={(r) => r.refund.refundID}
          rowProps={(r) => ({ id: `row-refund-${r.refund.refundID}`, className: keptRows.has(r.refund.refundID) || decisions.has(r.refund.refundID) ? "lms-decided-row" : undefined })}
          empty={
            <AllClear title={unresolvedOnly ? "All clear" : "No refunds"} action={unresolvedOnly ? <Button small level="quiet" onClick={() => changeFilter(false)}>Show all refunds</Button> : undefined}>
              {unresolvedOnly ? "No refund needs attention." : "There are no refunds to show."}
            </AllClear>
          }
        />}
        {result.data && <Pager label="Refund pages" page={result.data.page} pageCount={result.data.pageCount} onPage={(n) => { setKeptRows(new Map()); setDecisions(new Map()); set({ page: String(n) }); }} />}
      </Slip>

      {retry && (
        <ConfirmDialog
          title={`Retry the refund of order ${retry.order.orderReference}?`}
          confirmLabel="Retry refund"
          busyLabel="Retrying…"
          canConfirm={reasonReady(reason)}
          onClose={() => setRetry(null)}
          returnFocus={() => (focusAfter.current ? document.querySelector<HTMLElement>(focusAfter.current) : null)}
          onConfirm={async () => {
            const answer = await retryRefund(retry.refund.refundID, reason);
            if (!answer.ok) await updateRow(retry);
            return answer;
          }}
          onSuccess={(answer) => {
            if (answer.data) setDecisions((map) => new Map(map).set(retry.refund.refundID, answer.data!));
            counts.refresh();
            focusAfter.current = `#row-refund-${retry.refund.refundID} a`;
            setRetry(null);
            afterClose(() => toast.show("success", answer.message));
          }}
        >
          <p>The payment provider is asked again first. A new refund is requested only when the provider confirms that the old one failed.</p>
          <p className="text-small text-ink-muted">
            Refund {retry.refund.refundID}, {formatMoney(retry.refund.amount)}, requested <TimeText time={retry.refund.createdTime} />.
          </p>
          <ReasonField value={reason} onChange={setReason} />
        </ConfirmDialog>
      )}
    </>
  );
}
