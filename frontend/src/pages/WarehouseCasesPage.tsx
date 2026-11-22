import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router";
import { ArrowLeft, Download } from "lucide-react";
import { downloadCsv } from "../api/http";
import { apiTime, useWorkQuery, type DecidedRow } from "../api/liveWork";
import { decidedRefundLine, decideShortageCancel, decideShortageWait, loadPickingTasks, loadShortages, loadShortageTarget, loadWarehouseWorkers, reassignPickingTask, shortagePath, type LivePickingRow as PickingRow, type LiveShortageRow as ShortageRow } from "../api/liveCases";
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
  PageHeader,
  ProgressBar,
  Slip,
  SlipHeader,
  StatusBadge,
  TextLink,
  TimeText,
} from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { ConfirmDialog, afterClose } from "../components/ConfirmDialog";
import { CheckboxField, ReasonField, SelectField, reasonReady } from "../components/form";
import { SegmentedFilter } from "../components/SegmentedFilter";
import { CancelFacts } from "../components/CancelFacts";
import { useToast } from "../components/Toast";
import { useListParams } from "../lib/useListParams";
import { plainWord } from "../lib/format";

const CASE_FILTERS = [
  { value: "OPEN_AND_WAITING", label: "Open and waiting", statuses: ["OPEN", "WAITING_RESTOCK"] },
  { value: "OPEN", label: "Open", statuses: ["OPEN"] },
  { value: "WAITING_RESTOCK", label: "Waiting restock", statuses: ["WAITING_RESTOCK"] },
  { value: "RESOLVED", label: "Resolved", statuses: ["RESOLVED"] },
  { value: "CANCELLED", label: "Cancelled", statuses: ["CANCELLED"] },
];

async function loadFilterCounts() {
  const pages = await Promise.all(CASE_FILTERS.map(({ value }) => loadShortages(value, 1)));
  return CASE_FILTERS.map(({ value, label }, index) => ({ value, label, count: pages[index].total }));
}

type Open =
  | { kind: "wait"; row: ShortageRow }
  | { kind: "cancel"; row: ShortageRow }
  | { kind: "reassign"; row: PickingRow }
  | null;

function shortageText(row: ShortageRow) {
  return (
    <>
      <span className="font-semibold">
        {row.c.missingQuantity} of {row.c.orderedQuantity} missing
      </span>
      , {row.product?.SKU} {row.product?.name}
    </>
  );
}

export function WarehouseCasesPage() {
  const toast = useToast();
  const { values, set } = useListParams({ caseStatus: "OPEN_AND_WAITING", case: "", casePage: "1", taskPage: "1" });
  const location = useLocation();
  const fromDelivery = (location.state as { from?: string } | null)?.from === "delivery";
  const [keepCases, setKeepCases] = useState<Map<number, ShortageRow>>(new Map());
  const [keepTasks, setKeepTasks] = useState<Map<number, DecidedRow>>(new Map());
  const [open, setOpen] = useState<Open>(null);
  const [reason, setReason] = useState("");
  const [tick, setTick] = useState(false);
  const [worker, setWorker] = useState("");
  const focusAfter = useRef<string | null>(null);

  const [downloading, setDownloading] = useState(false);
  const casePage = Number(values.casePage) || 1;
  const taskPage = Number(values.taskPage) || 1;
  const target = Number(values.case) || 0;
  const casesResult = useWorkQuery(useCallback(() => loadShortageTarget(values.caseStatus, casePage, target), [values.caseStatus, casePage, target]));
  const tasksResult = useWorkQuery(useCallback(() => loadPickingTasks(taskPage), [taskPage]));
  const counts = useWorkQuery(loadFilterCounts);
  const choosingWorker = open?.kind === "reassign";
  const workers = useWorkQuery(useCallback(() => choosingWorker ? loadWarehouseWorkers() : Promise.resolve([]), [choosingWorker]));
  const eligible = workers.data?.filter((w) => open?.kind === "reassign" && w.id !== open.row.assignment.workerID) ?? [];
  const selectedWorker = eligible.find((w) => String(w.id) === worker) ?? eligible[0];
  const filterCounts: { value: string; label: string; count?: number }[] = counts.data ?? CASE_FILTERS;
  const cases = casesResult.data?.rows.map((r) => keepCases.get(r.c.shortageCaseID) ?? r) ?? [];
  const tasks = tasksResult.data?.rows ?? [];
  const openCount = counts.data?.find((f) => f.value === "OPEN")?.count;
  const waitingCount = counts.data?.find((f) => f.value === "WAITING_RESTOCK")?.count;

  useEffect(() => {
    if (!target || !casesResult.data) return;
    const frame = window.requestAnimationFrame(() => document.getElementById(`row-shortage-${target}`)?.scrollIntoView({ block: "center" }));
    return () => window.cancelAnimationFrame(frame);
  }, [target, casesResult.data]);

  const chooseStatus = (status: string) => {
    setKeepCases(new Map());
    set({ caseStatus: status, case: "", casePage: "1" });
  };

  const start = (next: Open) => {
    focusAfter.current = null;
    setReason("");
    setTick(false);
    setWorker("");
    setOpen(next);
  };
  const finish = (message: string, focusSelector: string) => {
    focusAfter.current = focusSelector;
    setOpen(null);
    afterClose(() => toast.show("success", message));
  };
  const saveCase = (row: ShortageRow, data: DecidedRow, cancelled: boolean) => {
    const decided: ShortageRow = {
      ...row,
      isPaid: cancelled ? data.refundID !== null : row.isPaid,
      order: cancelled ? { ...row.order, orderStatus: "CANCELLED" } : row.order,
      c: {
        ...row.c,
        caseStatus: cancelled ? "CANCELLED" : "WAITING_RESTOCK",
        decidedBy: data.adminDecidedRowDecidedByUsername, decidedTime: apiTime(data.adminDecidedRowDecidedTime),
        decisionReason: reason.trim(), refundNote: cancelled ? decidedRefundLine(data) : undefined,
      },
    };
    setKeepCases((current) => new Map(current).set(row.c.shortageCaseID, decided));
    counts.refresh();
    finish(data.message, `#row-shortage-${row.c.shortageCaseID} a`);
  };

  const downloadCases = async () => {
    setDownloading(true);
    try {
      await downloadCsv(shortagePath(values.caseStatus), "shortage-cases.csv");
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "The download failed.");
    } finally {
      setDownloading(false);
    }
  };

  const caseColumns: Column<ShortageRow>[] = [
    {
      key: "order",
      title: "Order",
      mobile: "title",
      cell: (r) => (
        <>
          <CellMain>
            <TextLink to={`/admin/orders/${r.order.orderID}`} state={{ from: "warehouse" }}>
              {r.order.orderReference}
            </TextLink>
          </CellMain>
          <CellSub className="font-normal">
            Order status: {plainWord(r.order.orderStatus).toLowerCase()}, {r.isPaid ? "paid" : "not paid"}
          </CellSub>
        </>
      ),
    },
    {
      key: "shortage",
      title: "Shortage",
      mobile: "wide",
      className: "min-w-[240px]",
      cell: (r) => (
        <>
          <p>{shortageText(r)}</p>
          {r.c.note && <CellSub>Note: “{r.c.note}”</CellSub>}
        </>
      ),
    },
    {
      key: "reported",
      title: "Reported",
      cell: (r) => (
        <>
          <TimeText time={r.c.reportedTime} />
          <CellSub>by {r.c.reportedBy}</CellSub>
        </>
      ),
    },
    {
      key: "status",
      title: "Status",
      className: "min-w-[200px]",
      cell: (r) => (
        <div data-decided-cell>
          <StatusBadge word={r.c.caseStatus} />
          {r.c.caseStatus === "WAITING_RESTOCK" && r.c.decidedBy && (
            <>
              <CellSub>
                Wait for restock, by {r.c.decidedBy}, <TimeText time={r.c.decidedTime} />
              </CellSub>
              {r.c.decisionReason && <CellSub>Reason: “{r.c.decisionReason}”</CellSub>}
            </>
          )}
          {r.c.caseStatus === "CANCELLED" && r.c.decidedBy && (
            <CellSub>
              Cancelled by {r.c.decidedBy}, <TimeText time={r.c.decidedTime} />. {r.c.refundNote}
            </CellSub>
          )}
          {r.c.caseStatus === "RESOLVED" && <CellSub>Resolved after restock</CellSub>}
        </div>
      ),
    },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      mobileHidden: (r) => r.c.caseStatus !== "OPEN" && r.c.caseStatus !== "WAITING_RESTOCK",
      cell: (r) => {
        const ref = r.order.orderReference;
        if (r.c.caseStatus !== "OPEN" && r.c.caseStatus !== "WAITING_RESTOCK") return <Empty />;
        return (
          <div className="flex flex-wrap gap-2">
            {r.c.caseStatus === "OPEN" && (
              <Button small aria-label={`Wait for restock for order ${ref}`} onClick={() => start({ kind: "wait", row: r })}>
                Wait for restock
              </Button>
            )}
            <Button small level="danger" aria-label={r.isPaid ? `Cancel and refund order ${ref}` : `Cancel order ${ref}`} onClick={() => start({ kind: "cancel", row: r })}>
              {r.isPaid ? "Cancel and refund" : "Cancel order"}
            </Button>
          </div>
        );
      },
    },
  ];

  const taskColumns: Column<PickingRow>[] = [
    {
      key: "order",
      title: "Order",
      mobile: "title",
      cell: (r) => (
        <CellMain>
          <TextLink to={`/admin/orders/${r.order.orderID}`} state={{ from: "warehouse" }}>
            {r.order.orderReference}
          </TextLink>
          {r.hasOpenShortage && <Flag family="turn">Open shortage case</Flag>}
        </CellMain>
      ),
    },
    {
      key: "worker",
      title: "Worker",
      cell: (r) => {
        const saved = keepTasks.get(r.task.pickingTaskID);
        return (
          <div data-decided-cell>
            <CellMain>
              <span className="font-medium">{saved?.adminDecidedRowWorkerUsername ?? r.assignment.workerUsername}</span>
              {!saved && r.workerNotActive && <Flag family="problem">Worker not active</Flag>}
            </CellMain>
            {saved && (
              <CellSub>
                Reassigned by {saved.adminDecidedRowDecidedByUsername}, <TimeText time={apiTime(saved.adminDecidedRowDecidedTime)} />
              </CellSub>
            )}
          </div>
        );
      },
    },
    { key: "assigned", title: "Assigned", cell: (r) => <TimeText time={r.assignment.assignedTime} /> },
    { key: "started", title: "Started", cell: (r) => <TimeText time={r.task.startedTime} /> },
    {
      key: "progress",
      title: "Progress",
      className: "min-w-[160px]",
      cell: (r) => (
        <div className="max-w-[220px]">
          <p id={`progress-${r.task.pickingTaskID}`} className="tabular">
            {r.picked} of {r.total} picked
          </p>
          <ProgressBar value={r.picked} max={r.total} labelID={`progress-${r.task.pickingTaskID}`} tone="teal" className="mt-1.5" />
        </div>
      ),
    },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      cell: (r) =>
        r.task.taskStatus === "IN_PROGRESS" && !keepTasks.has(r.task.pickingTaskID) ? (
          <Button small aria-label={`Reassign picking task of order ${r.order.orderReference}`} onClick={() => start({ kind: "reassign", row: r })}>
            Reassign
          </Button>
        ) : (
          <Empty />
        ),
    },
  ];

  const returnFocus = () => (focusAfter.current ? document.querySelector<HTMLElement>(focusAfter.current) : null);

  return (
    <>
      <PageHeader
        title="Warehouse cases"
        count={openCount === undefined || waitingCount === undefined ? undefined : openCount === 0 ? `Nothing waits for a decision. ${waitingCount} waiting for restock.` : `${openCount} open, ${waitingCount} waiting for restock`}
        back={
          fromDelivery ? (
            <Link to="/admin/delivery-cases" className="inline-flex min-h-10 items-center gap-1.5 text-label font-semibold text-teal hover:text-teal-hover hover:underline">
              <ArrowLeft aria-hidden="true" className="size-4" />
              Back to delivery cases
            </Link>
          ) : undefined
        }
      />

      <Slip labelledBy="shortageTitle">
        <SlipHeader
          title="Shortage cases"
          titleID="shortageTitle"
          count={casesResult.data?.total}
          controls={
            <Button small icon={<Download aria-hidden="true" className="size-4" />} onClick={downloadCases} busy={downloading} disabled={!casesResult.data || casesResult.pending}>
              Download CSV
            </Button>
          }
        />
        <div className="border-b border-line px-4 py-3 md:px-5">
          <SegmentedFilter label="Case status" options={filterCounts} value={values.caseStatus} onChange={chooseStatus} />
        </div>
        <WorkQueryNotice {...casesResult} />
        {casesResult.data && <DataTable
          cardsUntil="xl"
          label="Shortage cases table"
          columns={caseColumns}
          rows={cases}
          rowKey={(r) => r.c.shortageCaseID}
          rowProps={(r) => ({
            id: `row-shortage-${r.c.shortageCaseID}`,
            className: keepCases.has(r.c.shortageCaseID) ? "lms-decided-row" : values.case === String(r.c.shortageCaseID) ? "bg-turn-bg/55 [&>td]:bg-turn-bg/55" : undefined,
          })}
          empty={
            values.caseStatus === "OPEN" || values.caseStatus === "OPEN_AND_WAITING" ? (
              <AllClear title="All clear" action={<Button small level="quiet" onClick={() => chooseStatus("RESOLVED")}>Show resolved cases</Button>}>
                No shortage case waits for a decision.
              </AllClear>
            ) : (
              <AllClear title="No cases with this status">Choose another status to see more cases.</AllClear>
            )
          }
        />}
        {casesResult.data && <Pager label="Shortage case pages" page={casesResult.data.page} pageCount={casesResult.data.pageCount} onPage={(n) => { setKeepCases(new Map()); set({ casePage: String(n), case: "" }); }} />}
      </Slip>

      <Slip labelledBy="pickingTitle" className="mt-5">
        <SlipHeader title="Picking tasks in progress" titleID="pickingTitle" count={tasksResult.data?.total} />
        <WorkQueryNotice {...tasksResult} />
        {tasksResult.data && <DataTable
          cardsUntil="xl"
          label="Picking tasks table"
          columns={taskColumns}
          rows={tasks}
          rowKey={(r) => r.task.pickingTaskID}
          rowProps={(r) => ({ id: `row-picking-${r.task.pickingTaskID}`, className: keepTasks.has(r.task.pickingTaskID) ? "lms-decided-row" : undefined })}
          empty={<AllClear title="Nothing is being picked">Orders appear here while a Warehouse Worker picks them.</AllClear>}
        />}
        {tasksResult.data && <Pager label="Picking task pages" page={tasksResult.data.page} pageCount={tasksResult.data.pageCount} onPage={(n) => { setKeepTasks(new Map()); set({ taskPage: String(n) }); }} />}
      </Slip>

      {open?.kind === "wait" && (
        <ConfirmDialog
          title={`Wait for restock for order ${open.row.order.orderReference}?`}
          confirmLabel="Wait for restock"
          busyLabel="Saving…"
          canConfirm={reasonReady(reason)}
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          requirement="Type a reason of at least 5 characters to continue."
          onConfirm={() => decideShortageWait(open.row.c.shortageCaseID, reason)}
          onSuccess={(r) => { if (r.data) saveCase(open.row, r.data, false); }}
        >
          <FactList facts={[["Shortage", <span key="s">{shortageText(open.row)}</span>]]} />
          <p>The case waits for new stock. The order is not cancelled, and the client is told that it is delayed.</p>
          <ReasonField value={reason} onChange={setReason} />
        </ConfirmDialog>
      )}

      {open?.kind === "cancel" && (
        <ConfirmDialog
          title={open.row.isPaid ? `Cancel order ${open.row.order.orderReference} and refund?` : `Cancel order ${open.row.order.orderReference}?`}
          confirmLabel={open.row.isPaid ? "Cancel and refund" : "Cancel order"}
          busyLabel="Cancelling"
          level="danger-filled"
          closeLabel="Close"
          canConfirm={tick && reasonReady(reason)}
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          requirement="Tick the box and type a reason of at least 5 characters to continue."
          onConfirm={() => decideShortageCancel(open.row.c.shortageCaseID, reason, tick)}
          onSuccess={(r) => { if (r.data) saveCase(open.row, r.data, true); }}
        >
          <FactList facts={[["Shortage", <span key="s">{shortageText(open.row)}</span>]]} />
          <CancelFacts orderID={open.row.order.orderID} isPaid={open.row.isPaid} />
          <CheckboxField label="Warehouse has recorded the stock correction for this case" checked={tick} onChange={setTick} />
          <ReasonField value={reason} onChange={setReason} />
        </ConfirmDialog>
      )}

      {open?.kind === "reassign" && (
        <ConfirmDialog
          title={`Reassign the picking task of order ${open.row.order.orderReference}`}
          confirmLabel="Reassign task"
          busyLabel="Reassigning"
          canConfirm={reasonReady(reason) && Boolean(selectedWorker)}
          onClose={() => setOpen(null)}
          returnFocus={returnFocus}
          requirement={selectedWorker ? "Type a reason of at least 5 characters to continue." : "No other active Warehouse Worker can take the task."}
          onConfirm={() => reassignPickingTask(open.row.task.pickingTaskID, selectedWorker!.id, reason)}
          onSuccess={(r) => {
            if (r.data) setKeepTasks((s) => new Map(s).set(open.row.task.pickingTaskID, r.data!));
            finish(r.message, `#row-picking-${open.row.task.pickingTaskID} a`);
          }}
        >
          <FactList
            columns={2}
            facts={[
              ["Current worker", open.row.assignment.workerUsername],
              ["Progress", `${open.row.picked} of ${open.row.total} picked`],
            ]}
          />
          <WorkQueryNotice {...workers} />
          <SelectField
            label="New worker"
            value={selectedWorker ? String(selectedWorker.id) : ""}
            onChange={setWorker}
            options={eligible.map((w) => ({
              value: String(w.id),
              label: `${w.username} (${w.count} active ${w.count === 1 ? "task" : "tasks"})`,
            }))}
          />
          <ReasonField value={reason} onChange={setReason} />
          <p className="text-small text-ink-muted">The saved progress stays. The new worker must check the items against it before continuing.</p>
        </ConfirmDialog>
      )}
    </>
  );
}
