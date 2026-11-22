import { useCallback, useEffect, type ReactNode } from "react";
import { Link, useLocation, useParams } from "react-router";
import { ArrowLeft, SearchX, ShieldCheck } from "lucide-react";
import { loadOrderDetail } from "../api/liveOrders";
import { useWorkQuery } from "../api/liveWork";
import { WorkQueryNotice } from "../components/WorkQueryNotice";
import {
  ButtonLink,
  CellSub,
  Empty,
  EmptyState,
  FactList,
  Note,
  PageHeader,
  Part,
  Slip,
  SlipBody,
  SlipHeader,
  StatusBadge,
  TimeText,
} from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { JourneyRoute } from "../components/Route";
import { Timeline } from "../components/Timeline";
import { formatMoney, plainWord, plural } from "../lib/format";
import { useWideTables } from "../lib/useMediaQuery";

const BACK: Record<string, [string, string]> = {
  delivery: ["/admin/delivery-cases", "Back to delivery cases"],
  warehouse: ["/admin/warehouse-cases", "Back to warehouse cases"],
};

function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex min-h-10 items-center gap-1.5 rounded-control text-label font-semibold text-teal hover:text-teal-hover hover:underline">
      <ArrowLeft aria-hidden="true" className="size-4" />
      {children}
    </Link>
  );
}

export function OrderDetailPage() {
  const { reference = "" } = useParams();
  const location = useLocation();
  const wide = useWideTables();
  const result = useWorkQuery(useCallback(() => loadOrderDetail(reference), [reference]));
  const detail = result.data;
  const from = (location.state as { from?: string } | null)?.from;
  const [backTo, backLabel] = (from && BACK[from]) || ["/admin/orders", "Back to orders"];

  useEffect(() => {
    if (location.hash === "#shortage-cases") {
      window.requestAnimationFrame(() => document.getElementById("shortage-cases")?.scrollIntoView({ block: "start" }));
    }
  }, [location.hash, detail?.order.orderID]);

  if (result.pending || result.error) {
    return (
      <>
        <PageHeader title="Order" back={<BackLink to={backTo}>{backLabel}</BackLink>} />
        <Slip><WorkQueryNotice {...result} /></Slip>
      </>
    );
  }

  if (!detail) {
    return (
      <>
        <PageHeader title="Order not found" back={<BackLink to="/admin/orders">Back to orders</BackLink>} />
        <Slip>
          <EmptyState icon={<SearchX aria-hidden="true" className="size-7" />} title={`No order has the reference ${reference}.`}>
            Check the reference, or search for it on Orders.
          </EmptyState>
        </Slip>
      </>
    );
  }

  const { order } = detail;
  const total = detail.items.reduce((sum, i) => sum + i.lineTotal, 0);
  const openShortage = detail.shortageCases.some(({ c }) => c.caseStatus === "OPEN" || c.caseStatus === "WAITING_RESTOCK");

  const itemColumns: Column<(typeof detail.items)[number]>[] = [
    { key: "sku", title: "SKU", mobile: "title", cell: (i) => <span className="whitespace-nowrap tabular">{i.code}</span> },
    { key: "name", title: "Product", mobile: "wide", cell: (i) => <span className="font-normal">{i.name}</span> },
    { key: "quantity", title: "Quantity", numeric: true, cell: (i) => i.quantity },
    { key: "picked", title: "Picked", numeric: true, cell: (i) => i.picked },
    { key: "price", title: "Unit price", numeric: true, cell: (i) => formatMoney(i.unitPrice) },
    { key: "line", title: "Line total", numeric: true, cell: (i) => formatMoney(i.lineTotal) },
  ];

  return (
    <>
      <PageHeader
        back={<BackLink to={backTo}>{backLabel}</BackLink>}
        title={
          <>
            <span>Order {order.orderReference}</span>
            <StatusBadge word={order.orderStatus} />
          </>
        }
        count={`Client ${detail.client}, ${formatMoney(order.totalAmount)}`}
      />

      <Slip labelledBy="journeyTitle">
        <SlipHeader title="Journey" titleID="journeyTitle" />
        <SlipBody className="pt-6">
          <JourneyRoute
            stops={detail.journey.stops}
            now={detail.journey.now}
            decide={
              detail.instructions.some((i) => i.instructionStatus === "PENDING") && order.orderStatus === "DELIVERY_FAILED"
                ? { to: "/admin/delivery-cases?show=NEEDS_DECISION", label: "Decide on Delivery cases" }
                : detail.shortageCases.some(({ c }) => c.caseStatus === "OPEN")
                  ? { to: `/admin/warehouse-cases?case=${detail.shortageCases.find(({ c }) => c.caseStatus === "OPEN")!.c.shortageCaseID}`, label: "Decide on Warehouse cases" }
                  : null
            }
          />
        </SlipBody>
      </Slip>

      <div className="mt-5 grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)]">
        <Slip labelledBy="orderFactsTitle">
          <SlipHeader title="Order" titleID="orderFactsTitle" />
          <SlipBody className="space-y-4">
            <FactList
              columns={2}
              facts={[
                ["Client", detail.client],
                ["Created", <TimeText key="c" time={order.createdTime} />],
                ...(order.orderStatus === "AWAITING_PAYMENT" && order.paymentDeadline ? ([["Payment deadline", <TimeText key="d" time={order.paymentDeadline} />]] as Array<[string, ReactNode]>) : []),
                ["Total", formatMoney(order.totalAmount)],
                ["Delivery details", `${plural(order.deliveryDetailsVersionCount, "version")}, content not shown to Admin`],
              ]}
            />
            <p className="flex items-start gap-2 rounded-control bg-sunken px-3.5 py-2.5 text-small">
              <ShieldCheck aria-hidden="true" className="mt-px size-4 shrink-0 text-teal" />
              Address, phone number, recipient name and delivery proof are not shown to Admin.
            </p>
          </SlipBody>
        </Slip>

        <Slip labelledBy="itemsTitle">
          <SlipHeader title="Items" titleID="itemsTitle" count={detail.items.length} />
          <DataTable
            label="Items table"
            columns={itemColumns}
            rows={detail.items}
            rowKey={(i) => i.id}
            footer={
              <tfoot>
                <tr>
                  <th scope="row" colSpan={5} className="border-t-2 border-double border-line-strong px-3 py-3 pl-5 text-left font-semibold">
                    Total
                  </th>
                  <td className="border-t-2 border-double border-line-strong px-3 py-3 pr-5 text-right font-bold tabular">{formatMoney(total)}</td>
                </tr>
              </tfoot>
            }
            mobileFooter={
              <p className="flex justify-between border-t border-line px-4 py-3 font-bold">
                <span>Total</span>
                <span className="tabular">{formatMoney(total)}</span>
              </p>
            }
          />
        </Slip>
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.35fr)]">
        <Slip labelledBy="paymentTitle">
          <SlipHeader title="Payment" titleID="paymentTitle" count={detail.payments.length} />
          <DataTable
            label="Payments table"
            rows={detail.payments}
            rowKey={(p) => p.paymentID}
            empty={<p className="px-4 py-5 text-ink-muted md:px-5">No payment yet.</p>}
            columns={[
              { key: "amount", title: "Amount", numeric: true, mobile: "title", cell: (p) => formatMoney(p.amount) },
              { key: "status", title: "Status", mobile: "aside", cell: (p) => <StatusBadge word={p.paymentStatus} /> },
              {
                key: "times",
                title: "Times",
                mobile: "wide",
                cell: (p) => (
                  <>
                    <p>
                      Started <TimeText time={p.createdTime} />
                    </p>
                    {p.endedTime && (
                      <p>
                        {p.paymentStatus === "ACCEPTED" ? "Accepted" : "Ended"} <TimeText time={p.endedTime} />
                      </p>
                    )}
                  </>
                ),
              },
            ]}
          />
        </Slip>
        <Slip labelledBy="refundTitle">
          <SlipHeader title="Refund" titleID="refundTitle" count={detail.refunds.length} />
          <DataTable
            label="Refunds of this order"
            rows={detail.refunds}
            rowKey={(r) => r.refundID}
            empty={<p className="px-4 py-5 text-ink-muted md:px-5">No refund.</p>}
            columns={[
              { key: "refund", title: "Refund", mobile: "title", cell: (r) => <span className="whitespace-nowrap tabular">Refund {r.refundID}</span> },
              { key: "payment", title: "Payment", cell: (r) => <span className="tabular">{r.paymentID}</span> },
              { key: "amount", title: "Amount", numeric: true, cell: (r) => formatMoney(r.amount) },
              { key: "status", title: "Status", mobile: "aside", cell: (r) => <StatusBadge word={r.refundStatus} /> },
              {
                key: "times",
                title: "Times",
                mobile: "wide",
                cell: (r) => (
                  <>
                    <p>
                      Requested <TimeText time={r.createdTime} />
                    </p>
                    {r.completedTime && (
                      <p>
                        Completed <TimeText time={r.completedTime} />
                      </p>
                    )}
                  </>
                ),
              },
            ]}
          />
        </Slip>
      </div>

      <div className="mt-5 grid gap-5">
        <Slip labelledBy="pickingTitle">
          <SlipHeader title="Picking" titleID="pickingTitle" />
          <SlipBody className="space-y-4">
            {detail.task ? (
              <>
                <FactList
                  columns={wide ? 3 : 1}
                  className="lg:max-w-[760px]"
                  facts={[
                    ["Task status", <StatusBadge key="t" word={detail.task.taskStatus} />],
                    ["Started", <TimeText key="s" time={detail.task.startedTime} />],
                    ["Packing confirmed", <TimeText key="p" time={detail.task.packedTime} />],
                  ]}
                />
                <Part title="Workers" count={detail.assignments.length}>
                  <DataTable
                    label="Workers table"
                    rows={detail.assignments}
                    rowKey={(a) => a.assignmentID}
                    columns={[
                      { key: "worker", title: "Worker", mobile: "title", cell: (a) => a.workerUsername },
                      { key: "assigned", title: "Assigned", cell: (a) => <TimeText time={a.assignedTime} /> },
                      { key: "ended", title: "Ended", cell: (a) => <TimeText time={a.endedTime} /> },
                    ]}
                  />
                </Part>
              </>
            ) : (
              <p className="text-ink-muted">Picking has not started. A Warehouse Worker picks the order after payment.</p>
            )}
          </SlipBody>
        </Slip>

        <Slip labelledBy="deliveryTitle">
          <SlipHeader title="Delivery" titleID="deliveryTitle" />
          <SlipBody className="space-y-4">
            {detail.pkg ? (
              <FactList
                columns={wide ? 3 : 1}
                className="xl:grid-cols-4"
                facts={[
                  ["Package code", <span key="p" className="tabular">{detail.pkg.packageCode}</span>],
                  ["Where it is", <StatusBadge key="w" word={detail.pkg.location} />],
                  ["Held by", detail.pkg.heldBy ?? <Empty key="h" />],
                  ["Responsible worker", detail.pkg.responsibleWorker ?? <Empty key="r" />],
                ]}
              />
            ) : (
              <p className="text-ink-muted">No package yet. Warehouse makes one when the order is packed.</p>
            )}
            <Part title="Delivery attempts" count={detail.attempts.length}>
              <DataTable
                label="Delivery attempts table"
                rows={detail.attempts}
                rowKey={(a) => a.attemptID}
                minWidth="min-w-[760px]"
                empty={<p className="px-3.5 py-3 text-ink-muted">No rows found.</p>}
                columns={[
                  { key: "worker", title: "Worker", mobile: "title", cell: (a) => a.workerUsername },
                  { key: "started", title: "Started", cell: (a) => <TimeText time={a.startedTime} /> },
                  { key: "ended", title: "Ended", cell: (a) => <TimeText time={a.endedTime} /> },
                  { key: "outcome", title: "Outcome", mobile: "aside", cell: (a) => (a.outcome ? <StatusBadge word={a.outcome === "FAILED" ? "FAILED" : "DELIVERED"} /> : <StatusBadge word="OUT_FOR_DELIVERY" />) },
                  { key: "reason", title: "Reason", cell: (a) => a.reason ?? <Empty /> },
                  { key: "note", title: "Note", mobile: "wide", cell: (a) => a.note ?? <Empty /> },
                  { key: "proof", title: "Proof", mobile: "wide", cell: (a) => ((a.photos || a.signatures) ? `${plural(a.photos, "photo")}, ${plural(a.signatures, "signature")} (not viewable by Admin)` : <Empty />) },
                ]}
              />
            </Part>
            <Part title="Client requests" count={detail.instructions.length}>
              <DataTable
                label="Client requests table"
                rows={detail.instructions}
                rowKey={(i) => i.instructionID}
                minWidth="min-w-[760px]"
                empty={<p className="px-3.5 py-3 text-ink-muted">No rows found.</p>}
                columns={[
                  { key: "type", title: "Type", mobile: "title", cell: (i) => (i.instructionType === "REDELIVER" ? "Redeliver" : "Cancel") },
                  { key: "status", title: "Status", mobile: "aside", cell: (i) => <StatusBadge word={i.instructionStatus} /> },
                  { key: "source", title: "Entered through", cell: (i) => (i.source === "TRACKING_PAGE" || i.source === "CLIENT_TRACKING" ? "Tracking page" : "Admin") },
                  { key: "submitted", title: "Submitted", cell: (i) => <TimeText time={i.submittedTime} /> },
                  { key: "by", title: "Decided by", cell: (i) => i.decidedBy ?? <Empty /> },
                  { key: "decided", title: "Decided", cell: (i) => <TimeText time={i.decidedTime} /> },
                  { key: "note", title: "Note", mobile: "wide", cell: (i) => i.note ?? <Empty /> },
                ]}
              />
            </Part>
          </SlipBody>
        </Slip>
      </div>

      <Slip id="shortage-cases" labelledBy="shortageCasesTitle" className="mt-5 scroll-mt-20">
        <SlipHeader
          title="Shortage cases"
          titleID="shortageCasesTitle"
          count={detail.shortageCases.length}
          controls={
            openShortage ? (
              <ButtonLink small to="/admin/warehouse-cases?caseStatus=OPEN_AND_WAITING">
                Decide on Warehouse cases
              </ButtonLink>
            ) : undefined
          }
        />
        <DataTable
          label="Shortage cases of this order"
          rows={detail.shortageCases}
          rowKey={(s) => s.c.shortageCaseID}
          empty={<p className="px-4 py-5 text-ink-muted md:px-5">No shortage case.</p>}
          columns={[
            { key: "case", title: "Case", mobile: "title", cell: (s) => <span className="whitespace-nowrap tabular">Case {s.c.shortageCaseID}</span> },
            {
              key: "sku",
              title: "SKU",
              mobile: "wide",
              cell: (s) => (
                <>
                  {s.product.SKU}
                </>
              ),
            },
            { key: "missing", title: "Missing", cell: (s) => s.c.missingQuantity },
            { key: "status", title: "Status", mobile: "aside", cell: (s) => <StatusBadge word={s.c.caseStatus} /> },
            { key: "decision", title: "Decision", cell: (s) => (s.c.decision ? plainWord(s.c.decision) : <Empty />) },
            {
              key: "times",
              title: "Times",
              mobile: "wide",
              cell: (s) => (
                <>
                  <p>
                    Reported <TimeText time={s.c.reportedTime} />
                  </p>
                  {s.c.decidedTime && (
                    <CellSub className="text-body text-ink">
                      Decided <TimeText time={s.c.decidedTime} />
                    </CellSub>
                  )}
                </>
              ),
            },
          ]}
        />
      </Slip>

      <Slip labelledBy="timelineTitle" className="mt-5">
        <SlipHeader title="Timeline" titleID="timelineTitle" count={detail.days.reduce((sum, d) => sum + d.entries.length, 0)} />
        <SlipBody>
          <Timeline days={detail.days} />
          <Note className="mt-5">Times are shown in this browser’s time.</Note>
        </SlipBody>
      </Slip>
    </>
  );
}
