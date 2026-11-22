import { useCallback, useRef, useState, type ReactNode } from "react";
import { Download } from "lucide-react";
import { downloadCsv } from "../api/http";
import { apiTime, useWorkQuery, type DecidedRow } from "../api/liveWork";
import { approveCancellation, approveRedelivery, decidedRefundLine, failedCasesPath, findFailedCase, loadDeliveryWorkers, loadFailedCases, loadHeldPackages, reassignDelivery, recordClientInstruction, rejectClientInstruction, sendReminder, type LiveFailedDeliveryRow as FailedDeliveryRow, type LiveHeldRow as HeldPackageRow, type LiveInstruction } from "../api/liveCases";
import type { InstructionType } from "../api/types";
import { Pager } from "../components/Pager";
import { WorkQueryNotice } from "../components/WorkQueryNotice";
import {
  AllClear,
  Button,
  CellMain,
  CellSub,
  Empty,
  FactList,
  Flag,
  InlineMessage,
  MessagePlace,
  Note,
  PageHeader,
  Slip,
  SlipHeader,
  SlipStrip,
  StatusBadge,
  TextLink,
  TimeText,
} from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { ConfirmDialog, afterClose } from "../components/ConfirmDialog";
import { Dialog } from "../components/Dialog";
import { CheckboxField, ReasonField, SelectField, TextAreaField, TextField, reasonReady } from "../components/form";
import { SegmentedFilter } from "../components/SegmentedFilter";
import { CancelFacts } from "../components/CancelFacts";
import { useToast } from "../components/Toast";
import { useListParams } from "../lib/useListParams";
import { formatTime, plural } from "../lib/format";

type Open =
  | { kind: "redeliver"; row: FailedDeliveryRow }
  | { kind: "cancel"; row: FailedDeliveryRow }
  | { kind: "reject"; row: FailedDeliveryRow }
  | { kind: "record"; row: FailedDeliveryRow }
  | { kind: "reassign"; row: HeldPackageRow }
  | null;

interface Outcome {
  badge: string;
  line: ReactNode;
}

function requestWords(i: LiveInstruction, withComma: boolean): ReactNode {
  const type = i.instructionType === "REDELIVER" ? "Redelivery" : "Cancellation";
  const where = (i.source === "TRACKING_PAGE" || i.source === "CLIENT_TRACKING") ? " on the tracking page" : ", recorded by an Admin";
  return (
    <>
      {type}
      {withComma ? ", requested " : " requested "}
      <TimeText time={i.submittedTime} />
      {where}
    </>
  );
}

const FILTERS = [
  { value: "NEEDS_DECISION", label: "Needs decision" },
  { value: "NO_CLIENT_REQUEST", label: "Waiting for client" },
  { value: "WAITING_FOR_RETURN", label: "Waiting for return" },
  { value: "FLAGGED", label: "Flagged" },
  { value: "ALL", label: "All" },
];

async function loadFilterCounts() {
  const pages = await Promise.all(FILTERS.map(({ value }) => loadFailedCases(value, 1)));
  return FILTERS.map(({ value, label }, index) => ({ value, label, count: pages[index].total }));
}

export function DeliveryCasesPage() {
  const toast = useToast();
  const { values, set } = useListParams({ show: "ALL", page: "1", heldPage: "1" });
  const [outcomes, setOutcomes] = useState<Map<number, Outcome>>(new Map());
  const [heldDecisions, setHeldDecisions] = useState<Map<number, DecidedRow>>(new Map());
  const [open, setOpen] = useState<Open>(null);
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");
  const [worker, setWorker] = useState("");
  const [downloading, setDownloading] = useState(false);
  const [sendingReminder, setSendingReminder] = useState<number | null>(null);
  const focusAfter = useRef<string | null>(null);
  const page = Number(values.page) || 1;
  const heldPage = Number(values.heldPage) || 1;
  const cases = useWorkQuery(useCallback(() => loadFailedCases(values.show, page), [values.show, page]));
  const packages = useWorkQuery(useCallback(() => loadHeldPackages(heldPage), [heldPage]));
  const counts = useWorkQuery(loadFilterCounts);
  const choosingWorker = open?.kind === "reassign";
  const workers = useWorkQuery(useCallback(() => choosingWorker ? loadDeliveryWorkers() : Promise.resolve([]), [choosingWorker]));
  const eligible = workers.data?.filter((w) => open?.kind === "reassign" && w.id !== open.row.pkg.responsibleWorkerID) ?? [];
  const selectedWorker = eligible.find((w) => String(w.id) === worker) ?? eligible[0];
  const rows = cases.data?.rows ?? [];
  const held = packages.data?.rows ?? [];
  const thresholds = cases.data?.thresholds;
  const needDecision = counts.data?.find((f) => f.value === "NEEDS_DECISION")?.count;
  const filterCounts: { value: string; label: string; count?: number }[] = counts.data ?? FILTERS;

  const start = (next: Open) => {
    focusAfter.current = null;
    setReason("");
    setNote("");
    setWorker("");
    setOpen(next);
  };

  const chooseFilter = (show: string) => {
    setOutcomes(new Map());
    set({ show, page: "1" });
  };

  const remember = (orderID: number, data: DecidedRow, words: string, tail = "") => {
    const line = <>
      {words} by {data.adminDecidedRowDecidedByUsername}, <TimeText time={apiTime(data.adminDecidedRowDecidedTime)} />
      {tail}
    </>;
    setOutcomes((outcomes) => new Map(outcomes).set(orderID, { badge: data.adminDecidedRowStateWord, line }));
    counts.refresh();
    focusAfter.current = `#row-delivery-${orderID} a`;
    setOpen(null);
    afterClose(() => toast.show("success", data.message));
  };

  const download = async () => {
    setDownloading(true);
    try {
      await downloadCsv(failedCasesPath(values.show), "failed-deliveries.csv");
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "The download failed.");
    } finally {
      setDownloading(false);
    }
  };

  const columns: Column<FailedDeliveryRow>[] = [
    {
      key: "order",
      title: "Order",
      mobile: "title",
      cell: (r) => (
        <CellMain>
          <TextLink to={`/admin/orders/${r.order.orderID}`} state={{ from: "delivery" }}>
            {r.order.orderReference}
          </TextLink>
          {r.flags.review && r.order.orderStatus === "DELIVERY_FAILED" && <Flag family="turn">Review</Flag>}
          {r.hasOpenShortage && <Flag family="turn">Open shortage case</Flag>}
        </CellMain>
      ),
    },
    {
      key: "failure",
      title: "Failure",
      mobile: "wide",
      className: "min-w-[220px]",
      cell: (r) => (
        <>
          <p className="font-semibold">{r.latest?.reason ?? "-"}</p>
          {r.latest && (
            <CellSub>
              Failed {plural(r.failureCount, "time")}, latest <TimeText time={r.latest.endedTime} />, {r.latest.workerUsername}
            </CellSub>
          )}
          {r.latest?.note && <CellSub>Note: “{r.latest.note}”</CellSub>}
        </>
      ),
    },
    {
      key: "state",
      title: "State",
      mobile: "wide",
      className: "min-w-[230px]",
      cell: (r) => {
        const outcome = outcomes.get(r.order.orderID);
        if (outcome) {
          return (
            <div data-decided-cell>
              <StatusBadge word={outcome.badge} />
              <CellSub>{outcome.line}</CellSub>
            </div>
          );
        }
        return (
          <div data-decided-cell>
            <StatusBadge word={r.state} />
            {r.state === "WAITING_FOR_CLIENT" && (
              <>
                <CellSub>
                  Returned {plural(r.daysSinceReturn ?? 0, "day")} ago{" "}
                  {r.flags.finalReview && <Flag family="turn">Final review</Flag>}
                  {r.flags.reminderDue && <Flag family="waiting">Reminder due</Flag>}
                </CellSub>
                {r.pkg?.lastReminderTime && (
                  <CellSub>
                    Last reminder <TimeText time={r.pkg.lastReminderTime} />
                  </CellSub>
                )}
              </>
            )}
            {r.state === "NEEDS_DECISION" && r.pending && <CellSub>{requestWords(r.pending, false)}</CellSub>}
            {r.state === "WAITING_FOR_RETURN" && <CellSub>Waiting for return to Warehouse</CellSub>}
          </div>
        );
      },
    },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      mobileHidden: (r) => outcomes.has(r.order.orderID) || r.state === "WAITING_FOR_RETURN",
      className: "min-w-[210px]",
      cell: (r) => {
        const ref = r.order.orderReference;
        if (outcomes.has(r.order.orderID) || r.state === "WAITING_FOR_RETURN") return <Empty />;
        const record = (
          <Button small aria-label={`Record request for order ${ref}`} onClick={() => start({ kind: "record", row: r })}>
            Record request
          </Button>
        );
        if (r.state === "WAITING_FOR_CLIENT") {
          return (
            <div>
              <div className="flex flex-wrap gap-2">
                <Button
                  small
                  disabled={!r.canSendReminder || sendingReminder !== null}
                  busy={sendingReminder === r.order.orderID}
                  aria-label={`Send reminder for order ${ref}`}
                  onClick={async () => {
                    setSendingReminder(r.order.orderID);
                    const answer = await sendReminder(r.order.orderID);
                    if (answer.ok) cases.refresh();
                    setSendingReminder(null);
                    toast.show(answer.ok ? "success" : "error", answer.message);
                  }}
                >
                  Send reminder
                </Button>
                {record}
              </div>
              {r.nextReminderTime && (
                <CellSub>
                  Next reminder after <TimeText time={r.nextReminderTime} />.
                </CellSub>
              )}
            </div>
          );
        }
        const pending = r.pending!;
        const blocked = r.hasOpenShortage;
        return (
          <div>
            <div className="flex flex-wrap gap-2">
              {pending.instructionType === "REDELIVER" ? (
                <Button
                  small
                  blocked={blocked}
                  aria-describedby={blocked ? `blocked-${r.order.orderID}` : undefined}
                  aria-label={`Approve redelivery for order ${ref}`}
                  onClick={() => start({ kind: "redeliver", row: r })}
                >
                  Approve redelivery
                </Button>
              ) : (
                <Button small level="danger" blocked={blocked} aria-describedby={blocked ? `blocked-${r.order.orderID}` : undefined} aria-label={`Approve cancellation for order ${ref}`} onClick={() => start({ kind: "cancel", row: r })}>
                  Approve cancellation
                </Button>
              )}
              <Button small aria-label={`Reject request for order ${ref}`} onClick={() => start({ kind: "reject", row: r })}>
                Reject
              </Button>
              {record}
            </div>
            {blocked && (
              <>
                <CellSub>
                  <span id={`blocked-${r.order.orderID}`}>Decide the open shortage case first.</span>
                </CellSub>
                <CellSub>
                  <TextLink to={`/admin/orders/${r.order.orderID}#shortage-cases`} state={{ from: "delivery" }}>
                    View shortage cases for order {ref}
                  </TextLink>
                </CellSub>
              </>
            )}
          </div>
        );
      },
    },
  ];

  const heldColumns: Column<HeldPackageRow>[] = [
    {
      key: "order",
      title: "Order",
      mobile: "title",
      cell: (r) => (
        <>
          <CellMain>
            <TextLink to={`/admin/orders/${r.order.orderID}`} state={{ from: "delivery" }}>
              {r.order.orderReference}
            </TextLink>
          </CellMain>
          <CellSub>
            <StatusBadge word={r.order.orderStatus} />
          </CellSub>
        </>
      ),
    },
    { key: "package", title: "Package", cell: (r) => <span className="whitespace-nowrap tabular">{r.pkg.packageCode}</span> },
    {
      key: "heldBy",
      title: "Held by",
      className: "min-w-[260px]",
      cell: (r) => {
        const saved = heldDecisions.get(r.pkg.packageID);
        const responsible = saved?.adminDecidedRowWorkerUsername ?? r.pkg.responsibleWorker;
        const reassigned = saved ? apiTime(saved.adminDecidedRowDecidedTime) : r.pkg.lastReassignedTime;
        return (
          <div data-decided-cell>
            <CellMain>
              <span className="font-medium">{r.pkg.heldBy}</span>
              {r.holderNotActive && <Flag family="problem">Worker not active</Flag>}
            </CellMain>
            {responsible && responsible !== r.pkg.heldBy && (
              <CellSub>
                Responsible: <span className="font-semibold text-ink">{responsible}</span>
                {saved && `, reassigned by ${saved.adminDecidedRowDecidedByUsername}`}
                {reassigned && <>, <TimeText time={reassigned} /></>}
              </CellSub>
            )}
          </div>
        );
      },
    },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      cell: (r) => (
        heldDecisions.has(r.pkg.packageID) ? <Empty /> :
          <Button small aria-label={`Reassign delivery of order ${r.order.orderReference}`} onClick={() => start({ kind: "reassign", row: r })}>
            Reassign
          </Button>
      ),
    },
  ];

  const returnFocus = () => (focusAfter.current ? document.querySelector<HTMLElement>(focusAfter.current) : null);
  const ref = open && open.kind !== "reassign" ? open.row.order.orderReference : "";

  return (
    <>
      <PageHeader title="Delivery cases" count={needDecision === undefined ? undefined : needDecision === 0 ? "Nothing waits for a decision." : `${needDecision} need a decision`} />

      <Slip labelledBy="failedTitle">
        <SlipHeader
          title="Failed deliveries"
          titleID="failedTitle"
          count={cases.data?.total}
          controls={
            <Button small icon={<Download aria-hidden="true" className="size-4" />} onClick={download} busy={downloading} disabled={!cases.data || cases.pending}>
              Download CSV
            </Button>
          }
        />
        <div className="border-b border-line px-4 py-3 md:px-5">
          <SegmentedFilter label="Show" options={filterCounts} value={values.show} onChange={chooseFilter} />
        </div>
        {thresholds && <SlipStrip>
          <Note>
            Review at {thresholds.reviewFailureCount} failures. Reminder due after {thresholds.reminderDays} days. Final review after {thresholds.finalReviewDays} days. Nothing is cancelled automatically.
          </Note>
        </SlipStrip>}
        <WorkQueryNotice {...cases} />
        {cases.data && <DataTable
          cardsUntil="xl"
          label="Failed deliveries table"
          columns={columns}
          rows={rows}
          rowKey={(r) => r.order.orderID}
          rowProps={(r) => ({ id: `row-delivery-${r.order.orderID}`, className: outcomes.has(r.order.orderID) ? "lms-decided-row" : undefined })}
          empty={
            <AllClear title="All clear" action={values.show !== "ALL" ? <Button small level="quiet" onClick={() => chooseFilter("ALL")}>Show all failed deliveries</Button> : undefined}>
              {values.show === "NEEDS_DECISION" ? "No client request waits for a decision." : "No failed delivery matches this filter."}
            </AllClear>
          }
        />}
        {cases.data && <Pager label="Failed delivery pages" page={cases.data.page} pageCount={cases.data.pageCount} onPage={(n) => { setOutcomes(new Map()); set({ page: String(n) }); }} />}
      </Slip>

      <Slip labelledBy="heldTitle" className="mt-5">
        <SlipHeader title="Held packages" titleID="heldTitle" count={packages.data?.total} />
        <WorkQueryNotice {...packages} />
        {packages.data && <DataTable
          cardsUntil="xl"
          label="Held packages table"
          columns={heldColumns}
          rows={held}
          rowKey={(r) => r.pkg.packageID}
          rowProps={(r) => ({ id: `row-held-${r.pkg.packageID}`, className: heldDecisions.has(r.pkg.packageID) ? "lms-decided-row" : undefined })}
          empty={<AllClear title="No package is on the road">Packages appear here while a Delivery Worker holds them.</AllClear>}
        />}
        {packages.data && <Pager label="Held package pages" page={packages.data.page} pageCount={packages.data.pageCount} onPage={(n) => { setHeldDecisions(new Map()); set({ heldPage: String(n) }); }} />}
      </Slip>

      {open?.kind === "redeliver" && (
        <ConfirmDialog
          title={`Approve redelivery of order ${ref}?`}
          confirmLabel="Approve redelivery"
          busyLabel="Approving"
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          onConfirm={() => approveRedelivery(open.row.pending!.instructionID, note)}
          onSuccess={(r) => { if (r.data) remember(open.row.order.orderID, r.data, "Redelivery approved"); }}
        >
          <FactList
            facts={[
              ["Request", <span key="r">{open.row.pending && requestWords(open.row.pending, true)}</span>],
              [
                "Failures",
                <span key="f">
                  {plural(open.row.failureCount, "failed attempt")}. Latest: {open.row.latest?.reason}, <TimeText time={open.row.latest?.endedTime} />, {open.row.latest?.workerUsername}
                  {open.row.latest?.note ? `. Note: \u201c${open.row.latest.note}\u201d` : ""}
                </span>,
              ],
            ]}
          />
          <p>The order becomes Ready for redelivery, and Warehouse sends the package out again. The client is told.</p>
          <TextAreaField label="Note" optional value={note} onChange={setNote} />
        </ConfirmDialog>
      )}

      {open?.kind === "cancel" && (
        <ConfirmDialog
          title={`Approve cancellation of order ${ref}?`}
          confirmLabel="Approve cancellation"
          busyLabel="Cancelling"
          level="danger-filled"
          closeLabel="Close"
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          onConfirm={() => approveCancellation(open.row.pending!.instructionID, note)}
          onSuccess={(r) => { if (r.data) remember(open.row.order.orderID, r.data, "Cancellation approved", `. ${decidedRefundLine(r.data)}`); }}
        >
          <FactList facts={[["Request", <span key="r">{open.row.pending && requestWords(open.row.pending, true)}</span>]]} />
          <CancelFacts orderID={open.row.order.orderID} isPaid={open.row.isPaid} closesPendingRequest={false} />
          <TextAreaField label="Note" optional value={note} onChange={setNote} />
        </ConfirmDialog>
      )}

      {open?.kind === "reject" && (
        <ConfirmDialog
          title={`Reject the request for order ${ref}?`}
          confirmLabel="Reject request"
          busyLabel="Rejecting"
          canConfirm={reasonReady(reason)}
          requirement="Type a reason of at least 5 characters to continue."
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          onConfirm={() => rejectClientInstruction(open.row.pending!.instructionID, reason)}
          onSuccess={(r) => { if (r.data) remember(open.row.order.orderID, r.data, "Request rejected"); }}
        >
          <FactList facts={[["Request", <span key="r">{open.row.pending && requestWords(open.row.pending, true)}</span>]]} />
          <p>The order stays as it is. The client is told the reason and can send a new request.</p>
          <ReasonField value={reason} onChange={setReason} />
        </ConfirmDialog>
      )}

      {open?.kind === "record" && (
        <RecordRequestDialog
          row={open.row}
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          onDone={(data) => remember(open.row.order.orderID, data, "Request recorded")}
        />
      )}

      {open?.kind === "reassign" && (
        <ConfirmDialog
          title={`Reassign the delivery of order ${open.row.order.orderReference}`}
          confirmLabel="Reassign delivery"
          busyLabel="Reassigning"
          canConfirm={reasonReady(reason) && Boolean(selectedWorker)}
          requirement={selectedWorker ? "Type a reason of at least 5 characters to continue." : "No other active Delivery Worker can take the delivery."}
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          onConfirm={() => reassignDelivery(open.row.pkg.packageID, selectedWorker!.id, reason)}
          onSuccess={(r) => {
            if (r.data) setHeldDecisions((previous) => new Map(previous).set(open.row.pkg.packageID, r.data!));
            focusAfter.current = `#row-held-${open.row.pkg.packageID} a`;
            setOpen(null);
            afterClose(() => toast.show("success", r.message));
          }}
        >
          <FactList
            columns={2}
            facts={[
              ["Held by", open.row.pkg.heldBy ?? "-"],
              ["Responsible worker", open.row.pkg.responsibleWorker ?? "-"],
            ]}
          />
          <WorkQueryNotice {...workers} />
          <SelectField
            label="New worker"
            value={selectedWorker ? String(selectedWorker.id) : ""}
            onChange={setWorker}
            options={eligible.map((w) => ({
              value: String(w.id),
              label: `${w.username} (${w.held} held, ${w.responsible} responsible)`,
            }))}
          />
          <ReasonField value={reason} onChange={setReason} />
          <p className="text-small text-ink-muted">The package only changes hands after Warehouse confirms its return and the new worker collects it.</p>
        </ConfirmDialog>
      )}
    </>
  );
}

/** W-D4: record a request for a client who cannot use the tracking page. It remembers the pending request it showed. */
function RecordRequestDialog({ row, onClose, onDone, returnFocus }: { row: FailedDeliveryRow; onClose: () => void; onDone: (data: DecidedRow) => void; returnFocus: () => HTMLElement | null }) {
  const [shownPending, setShownPending] = useState(row.pending);
  const [type, setType] = useState<InstructionType>("REDELIVER");
  const [tick, setTick] = useState(false);
  const [method, setMethod] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ text: string; conflict: boolean } | null>(null);
  const ref = row.order.orderReference;

  const confirm = async () => {
    setBusy(true);
    setError(null);
    const answer = await recordClientInstruction(row.order.orderID, type, tick, method, shownPending?.instructionID ?? null);
    setBusy(false);
    if (answer.ok && answer.data) onDone(answer.data);
    else setError({ text: answer.message, conflict: answer.field === "conflict" });
  };

  return (
    <Dialog
      title={`Record a client request for order ${ref}`}
      onClose={onClose}
      returnFocus={returnFocus}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            level="main"
            disabled={!tick || !reasonReady(method) || Boolean(error?.conflict)}
            aria-describedby={!tick || !reasonReady(method) ? "recordRequestRequirement" : undefined}
            busy={busy}
            busyText="Recording…"
            onClick={confirm}
          >
            Record request
          </Button>
        </>
      }
    >
      {error?.conflict && (
        <InlineMessage kind="error">
          <p>{error.text}</p>
          <Button
            small
            level="quiet"
            className="-ml-2 mt-1"
            busy={busy}
            onClick={async () => {
              setBusy(true);
              try {
                const fresh = await findFailedCase(row.order.orderID);
                if (!fresh || fresh.state === "WAITING_FOR_RETURN") {
                  setError({ text: "This order is no longer ready for a client request. Close the form and refresh the list.", conflict: true });
                  return;
                }
                setShownPending(fresh.pending);
                setType("REDELIVER");
                setTick(false);
                setMethod("");
                setError(null);
              } catch (error) {
                setError({ text: error instanceof Error ? error.message : "The request could not be refreshed.", conflict: true });
              } finally {
                setBusy(false);
              }
            }}
          >
            Refresh request
          </Button>
        </InlineMessage>
      )}
      <p>Use this when the client cannot use the tracking page.</p>
      {shownPending && (
        <p className="rounded-control bg-sunken px-3.5 py-2.5 text-small">
          This replaces the pending request: {shownPending.instructionType === "REDELIVER" ? "Redelivery" : "Cancellation"}, {formatTime(shownPending.submittedTime)}. That request is withdrawn.
        </p>
      )}
      <SelectField
        label="Request type"
        value={type}
        onChange={(v) => setType(v as InstructionType)}
        options={[
          { value: "REDELIVER", label: "Redelivery" },
          { value: "CANCEL", label: "Cancellation" },
        ]}
      />
      <CheckboxField label="I have checked who this person is" checked={tick} onChange={setTick} />
      <TextField label="How was the person checked?" value={method} onChange={setMethod} help="At least 5 characters, for example: caller answered the account questions." autoComplete="off" />
      <p className="text-small text-ink-muted">You cannot enter a new address or phone number. A client who needs different delivery details must use the tracking page.</p>
      {(!tick || !reasonReady(method)) && !error?.conflict && (
        <p id="recordRequestRequirement" className="text-small text-ink-muted">
          Tick the box and say how you checked the person (at least 5 characters) to continue.
        </p>
      )}
      <MessagePlace message={error && !error.conflict ? { kind: "error", text: error.text } : null} />
    </Dialog>
  );
}
