import { useRef, useState, type FormEvent } from "react";
import { useAdminRead } from "../api/adminLists";
import * as tools from "../api/liveTestTools";
import type { AccountStatus, ActionResult, ProviderAnswer, RefundStatus, Role } from "../api/types";
import { Button, Flag, InlineMessage, PageHeader, Slip } from "../components/ui";
import { HowToUse } from "../components/HowToUse";
import { CheckboxField, SelectBox, SelectField, TextField } from "../components/form";
import { cn } from "../lib/cn";
import { useFocusFirstInvalid } from "../lib/useFocusFirstInvalid";

type FieldSpec =
  | { name: string; kind: "text" | "number"; label: string; placeholder?: string; help?: string; optional?: boolean; initial?: string; min?: number; max?: number }
  | { name: string; kind: "select"; label: string; options: Array<{ value: string; label: string }>; help?: string }
  | { name: string; kind: "checkbox"; label: string; help?: string };

interface ToolSpec {
  id: string;
  stage: number;
  title: string;
  sentence: string;
  how: Array<[string, string]>;
  fields: FieldSpec[];
  button: string;
  danger?: boolean;
  run: (v: Record<string, string>) => Promise<ActionResult>;
}

const STAGES: Record<number, string> = {
  1: "Login and accounts",
  2: "Users list and active work",
  3: "Logs",
  4: "Products",
  5: "Orders",
  6: "Warehouse cases",
  7: "Delivery cases",
  8: "Refunds",
  9: "Final checks",
};

const TOOLS: ToolSpec[] = [
  {
    id: "createTestUser",
    stage: 1,
    title: "Create test user",
    sentence: "Creates an active account with a temporary password.",
    how: [
      ["Creates", "An active account named after its role, for example test_warehouse_3, with a temporary password that is valid for 24 hours."],
      ["See it on", "Users. The first login with this password asks for a new one."],
    ],
    fields: [
      {
        name: "role",
        kind: "select",
        label: "Role",
        options: [
          { value: "CLIENT", label: "Client" },
          { value: "WAREHOUSE_WORKER", label: "Warehouse Worker" },
          { value: "DELIVERY_WORKER", label: "Delivery Worker" },
          { value: "ADMIN", label: "Admin" },
        ],
      },
    ],
    button: "Create test user",
    run: (v) => tools.createTestUser(v.role as Role),
  },
  {
    id: "endLock",
    stage: 1,
    title: "End lock",
    sentence: "Ends the 15-minute lock of an account, as if the time had run out.",
    how: [
      ["Changes", "The 15-minute lock of the account ends now, as if the time had run out."],
      ["Before", "Lock the account with 5 wrong passwords on the login page."],
      ["See it on", "Users: the account can log in again."],
    ],
    fields: [{ name: "username", kind: "text", label: "Username", placeholder: "e.g. wh_picker_01" }],
    button: "End lock",
    run: (v) => tools.endLock(v.username),
  },
  {
    id: "expireTemporaryPassword",
    stage: 1,
    title: "Expire temporary password",
    sentence: "Lets the temporary password of an account run out at once.",
    how: [
      ["Changes", "The temporary password of the account runs out now."],
      ["Before", 'Give the account a temporary password with “Create test user” or “Reset password”.'],
      ["Not for", "The super admin."],
      ["See it on", "The login page: the password no longer works."],
    ],
    fields: [{ name: "username", kind: "text", label: "Username", placeholder: "e.g. test_warehouse_3" }],
    button: "Expire temporary password",
    run: (v) => tools.expireTemporaryPassword(v.username),
  },
  {
    id: "setUserStatus",
    stage: 1,
    title: "Set user status",
    sentence: "Sets only the stored status of an account.",
    how: [
      ["Changes", "Only the stored status. Everything else about the account stays as it is."],
      ["Not for", "The super admin."],
      ["See it on", "Users, after a reload."],
    ],
    fields: [
      { name: "username", kind: "text", label: "Username", placeholder: "e.g. test_client_4" },
      {
        name: "status",
        kind: "select",
        label: "Status",
        options: [
          { value: "ACTIVE", label: "Active" },
          { value: "INACTIVE", label: "Deactivated" },
          { value: "BANNED", label: "Banned" },
          { value: "LOCKED", label: "Locked (old status)" },
        ],
      },
    ],
    button: "Set user status",
    run: (v) => tools.setUserStatus(v.username, v.status as AccountStatus),
  },
  {
    id: "createManyTestUsers",
    stage: 2,
    title: "Create many test users",
    sentence: "Creates 25 Client accounts, so that page numbers can be tested.",
    how: [],
    fields: [],
    button: "Create many test users",
    run: () => tools.createManyTestUsers(),
  },
  {
    id: "createWorkerWithActiveWork",
    stage: 2,
    title: "Create worker with active work",
    sentence: "Creates a Warehouse Worker and a Delivery Worker who both have work in progress.",
    how: [
      ["Creates", "A Warehouse Worker with 2 orders being picked, and a Delivery Worker who holds 1 package and has claimed 1 pickup list."],
      ["Shows", "Both usernames, their temporary passwords and the order references."],
      ["Next", 'Try “Change role”, “Ban” or “Deactivate” on Users: each names the active work.'],
    ],
    fields: [],
    button: "Create worker with active work",
    run: () => tools.createWorkerWithActiveWork(),
  },
  {
    id: "createPickingScanRows",
    stage: 3,
    title: "Create picking scan rows",
    sentence: "Creates a sample order that is being picked, with six scan rows.",
    how: [
      ["Creates", "One order being picked, with 6 scan rows: 3 accepted, 1 reversed and 2 rejected."],
      ["Shows", "The order reference."],
      ["See it on", "Logs, tab Picking scans, with the reference as the order filter."],
    ],
    fields: [],
    button: "Create picking scan rows",
    run: () => tools.createPickingScanRows(),
  },
  {
    id: "createOldLogRows",
    stage: 3,
    title: "Create old log rows",
    sentence: "Adds log rows just older and just newer than the keeping times.",
    how: [
      ["Creates", "Log rows just older and just newer than the keeping times. A second press replaces them."],
      ["Next", 'Press “Run log clean-up now”: only the newer rows stay in Logs.'],
    ],
    fields: [],
    button: "Create old log rows",
    run: () => tools.createOldLogRows(),
  },
  {
    id: "runLogCleanupNow",
    stage: 3,
    title: "Run log clean-up now",
    sentence: "Runs the daily clean-up of old log rows at once.",
    how: [["See it on", 'Logs: the card “Log clean-up” shows the run and the rows deleted.']],
    fields: [],
    button: "Run log clean-up now",
    run: () => tools.runLogCleanupNow(),
  },
  {
    id: "createPrivateValueLogRows",
    stage: 3,
    title: "Create private value log rows",
    sentence: "Adds log rows that hold private test values.",
    how: [
      ["Creates", "One audit row and one security row that hold private test values, such as a phone number."],
      ["See it on", "Logs: the private values are hidden."],
    ],
    fields: [],
    button: "Create private value log rows",
    run: () => tools.createPrivateValueLogRows(),
  },
  {
    id: "setProductStock",
    stage: 4,
    title: "Set product stock",
    sentence: "Sets the physical and reserved stock of one product.",
    how: [
      ["Changes", "The two stock numbers of the product, which Admin itself may not change."],
      ["See it on", 'Products, with the flags “Low stock” and “Shortfall”.'],
    ],
    fields: [
      { name: "sku", kind: "text", label: "SKU", placeholder: "e.g. SKU-1002" },
      { name: "physical", kind: "number", label: "Physical stock", placeholder: "e.g. 10", help: "Available stock is physical minus reserved, but never below 0.", min: 0 },
      { name: "reserved", kind: "number", label: "Reserved stock", placeholder: "e.g. 2", help: 'More reserved than physical shows the flag “Shortfall”.', min: 0 },
    ],
    button: "Set product stock",
    run: (v) => tools.setProductStock(v.sku, v.physical, v.reserved),
  },
  {
    id: "createSampleOrderSet",
    stage: 5,
    title: "Create sample order set",
    sentence: "Creates one sample order in each of the ten order statuses.",
    how: [
      ["Creates", "10 orders, one in each order status."],
      ["Shows", "The 10 order references with their status."],
      ["See it on", "Orders, and the order page of each reference."],
    ],
    fields: [],
    button: "Create sample order set",
    run: () => tools.createSampleOrderSet(),
  },
  {
    id: "createShortageReport",
    stage: 6,
    title: "Create shortage report",
    sentence: "Reports a missing item of a sample order, as Warehouse would.",
    how: [
      ["Creates", "A shortage case, as Warehouse reports it. With the order reference empty, also a new sample order with 3 mugs and 1 pen."],
      ["Shows", "The order reference and the case number."],
      ["See it on", "Warehouse cases."],
      ["Example", "Press it with the order reference empty, then again with the reference it showed: that order then has two cases."],
    ],
    fields: [
      {
        name: "status",
        kind: "select",
        label: "Order status",
        options: [
          { value: "AWAITING_PAYMENT", label: "Awaiting payment" },
          { value: "PICKING", label: "Picking" },
          { value: "READY_FOR_PICKUP", label: "Ready for pickup" },
        ],
      },
      { name: "missing", kind: "number", label: "Missing quantity", initial: "1", min: 1, help: "From 1 to the number ordered: up to 3 for the mug, 1 for the pen." },
      { name: "reference", kind: "text", label: "Order reference", optional: true, placeholder: "e.g. TEST-0005", help: "Empty: a new sample order whose mug is missing. Filled: the pen of that order is missing." },
    ],
    button: "Create shortage report",
    run: (v) => tools.createShortageReport(v.status as "AWAITING_PAYMENT" | "PICKING" | "READY_FOR_PICKUP", v.missing, v.reference),
  },
  {
    id: "markShortageResolved",
    stage: 6,
    title: "Mark shortage resolved",
    sentence: "Closes a waiting shortage case, as Warehouse would after restock.",
    how: [
      ["Before", 'Decide “Wait for restock” for the case on Warehouse cases.'],
      ["See it on", "Warehouse cases, with the filter Resolved."],
    ],
    fields: [{ name: "case", kind: "number", label: "Case number", placeholder: "e.g. 8", min: 1 }],
    button: "Mark shortage resolved",
    run: (v) => tools.markShortageResolved(v.case),
  },
  {
    id: "createPickingOrder",
    stage: 6,
    title: "Create picking order",
    sentence: "Creates a sample order that a Warehouse Worker is picking.",
    how: [
      ["Creates", "An order being picked, with 1 of 3 mugs picked."],
      ["Shows", "The order reference and the worker."],
      ["See it on", "Warehouse cases, card Picking tasks in progress."],
    ],
    fields: [{ name: "worker", kind: "text", label: "Worker username", optional: true, placeholder: "e.g. wh_picker_04", help: "Empty: a new test worker." }],
    button: "Create picking order",
    run: (v) => tools.createPickingOrder(v.worker),
  },
  {
    id: "createFailedDeliveryCase",
    stage: 7,
    title: "Create failed delivery case",
    sentence: "Creates a sample order whose delivery failed.",
    how: [
      ["Creates", "An order whose delivery failed, with that many failed attempts."],
      ["Shows", "The order reference and the request number."],
      ["See it on", "Delivery cases."],
      ["Example", '1 failure, returned 8 days ago, no request: the flag “Final review”.'],
    ],
    fields: [
      { name: "failures", kind: "number", label: "Failures", placeholder: "e.g. 2", min: 1, max: 5, help: '1 to 5. 2 or more show the flag “Review”.' },
      { name: "returned", kind: "checkbox", label: "Returned to Warehouse" },
      {
        name: "days",
        kind: "number",
        label: "Days since return",
        placeholder: "e.g. 3",
        min: 0,
        max: 30,
        help: '0 to 30, only with “Returned to Warehouse”. 3 days show “Reminder due”, 7 days “Final review”, unless a client request waits.',
      },
      {
        name: "request",
        kind: "select",
        label: "Client request",
        options: [
          { value: "NONE", label: "None" },
          { value: "REDELIVER", label: "Redeliver" },
          { value: "CANCEL", label: "Cancel" },
        ],
        help: "Redeliver or Cancel adds a request that waits for a decision.",
      },
    ],
    button: "Create failed delivery case",
    run: (v) => tools.createFailedDeliveryCase(v.failures, v.returned === "true", v.days, v.request as "NONE" | "REDELIVER" | "CANCEL"),
  },
  {
    id: "createHeldPackage",
    stage: 7,
    title: "Create held package",
    sentence: "Creates a sample order whose package a Delivery Worker holds.",
    how: [
      ["Creates", "An order whose package a Delivery Worker holds."],
      ["Shows", "The order reference, the package number and the worker."],
      ["See it on", "Delivery cases, card Held packages."],
    ],
    fields: [
      { name: "worker", kind: "text", label: "Worker username", optional: true, placeholder: "e.g. dl_driver_04", help: "Empty: a new test worker." },
      {
        name: "status",
        kind: "select",
        label: "Order status",
        options: [
          { value: "SHIPPED", label: "Shipped" },
          { value: "OUT_FOR_DELIVERY", label: "Out for delivery" },
        ],
      },
    ],
    button: "Create held package",
    run: (v) => tools.createHeldPackage(v.worker, v.status as "SHIPPED" | "OUT_FOR_DELIVERY"),
  },
  {
    id: "createRefundCase",
    stage: 8,
    title: "Create refund case",
    sentence: "Creates a cancelled sample order with one refund.",
    how: [
      ["Creates", "A cancelled order with an accepted payment and one refund in the chosen status."],
      ["Shows", "The order reference, the refund number and the payment number."],
      ["See it on", "Refunds."],
    ],
    fields: [
      {
        name: "status",
        kind: "select",
        label: "Refund status",
        options: [
          { value: "REQUESTED", label: "Requested" },
          { value: "PENDING", label: "Pending" },
          { value: "FAILED", label: "Failed" },
          { value: "REFUNDED", label: "Refunded" },
        ],
      },
      { name: "age", kind: "number", label: "Age in hours", placeholder: "e.g. 30", min: 0, max: 720, help: "0 to 720, which is 30 days." },
    ],
    button: "Create refund case",
    run: (v) => tools.createRefundCase(v.status as RefundStatus, v.age),
  },
  {
    id: "setProviderAnswer",
    stage: 8,
    title: "Set provider answer",
    sentence: "Sets the answer that the stand-in payment provider gives for one refund.",
    how: [
      ["Changes", "The answer that the stand-in payment provider gives for that refund."],
      ["Next", 'Press “Check provider status” in the row of that refund on Refunds.'],
    ],
    fields: [
      { name: "refund", kind: "number", label: "Refund number", placeholder: "e.g. 12", min: 1 },
      {
        name: "outcome",
        kind: "select",
        label: "Outcome",
        options: [
          { value: "REFUNDED", label: "Refunded" },
          { value: "FAILED", label: "Failed" },
          { value: "PENDING", label: "Pending" },
          { value: "UNKNOWN", label: "Unknown" },
        ],
      },
    ],
    button: "Set provider answer",
    run: (v) => tools.setProviderAnswer(v.refund, v.outcome as ProviderAnswer),
  },
  {
    id: "runPrivacyCheck",
    stage: 9,
    title: "Run privacy check",
    sentence: "Searches every Admin answer for private data.",
    how: [["Shows", "How many answers were checked, and each finding with its function and what was found."]],
    fields: [],
    button: "Run privacy check",
    run: () => tools.runPrivacyCheck(),
  },
  {
    id: "removeAllTestData",
    stage: 9,
    title: "Remove all test data",
    sentence: "Empties every table and removes every test account. Your own account and the real accounts stay. This cannot be undone.",
    how: [
      ["Removes", "Every row of every table and every test account. This cannot be undone."],
      ["Keeps", "Your own account and every account that is not a test account."],
    ],
    fields: [{ name: "confirm", kind: "text", label: "Type REMOVE to confirm" }],
    button: "Remove all test data",
    danger: true,
    run: (v) => tools.removeAllTestData(v.confirm),
  },
];

function initialValues(spec: ToolSpec): Record<string, string> {
  const values: Record<string, string> = {};
  for (const field of spec.fields) {
    if (field.kind === "select") values[field.name] = field.options[0].value;
    else if (field.kind === "checkbox") values[field.name] = "false";
    else values[field.name] = field.initial ?? "";
  }
  return values;
}

function ToolCard({ spec }: { spec: ToolSpec }) {
  const [values, setValues] = useState(() => initialValues(spec));
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ActionResult | null>(null);
  const titleID = `tool-${spec.id}`;
  const formRef = useRef<HTMLFormElement>(null);
  useFocusFirstInvalid(result && !result.ok && result.field ? result : null, formRef);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy || disabled) return;
    setBusy(true);
    setResult(null);
    const answer = await spec.run(values);
    setBusy(false);
    setResult(answer);
    if (answer.ok && spec.id === "removeAllTestData") setValues(initialValues(spec));
  };

  const update = (name: string, value: string) => setValues((v) => ({ ...v, [name]: value }));
  const disabled = spec.id === "removeAllTestData" && values.confirm !== "REMOVE";

  return (
    <Slip labelledBy={titleID} className={cn("flex flex-col", spec.danger && "border-danger/40")}>
      <form ref={formRef} onSubmit={submit} noValidate className="flex flex-1 flex-col gap-4 px-4 py-4 md:px-5">
        <div>
          <h3 id={titleID} className="text-card-title font-[650]">
            {spec.title}
          </h3>
          <p className="mt-1 text-ink-muted">{spec.sentence}</p>
        </div>
        <HowToUse lines={spec.how} />
        {spec.fields.length > 0 && (
          <div className="grid gap-4">
            {spec.fields.map((field) => {
              const error = result && !result.ok && result.field === field.name ? result.message : null;
              if (field.kind === "select")
                return <SelectField key={field.name} label={field.label} value={values[field.name]} onChange={(v) => update(field.name, v)} options={field.options} help={field.help} />;
              if (field.kind === "checkbox")
                return <CheckboxField key={field.name} label={field.label} checked={values[field.name] === "true"} onChange={(c) => update(field.name, c ? "true" : "false")} help={field.help} />;
              return (
                <TextField
                  key={field.name}
                  label={field.label}
                  optional={field.optional}
                  value={values[field.name]}
                  onChange={(v) => update(field.name, v)}
                  placeholder={field.placeholder}
                  help={field.help}
                  error={error}
                  type={field.kind === "number" ? "number" : "text"}
                  inputMode={field.kind === "number" ? "numeric" : undefined}
                  min={field.min}
                  max={field.max}
                  autoComplete="off"
                  spellCheck={false}
                />
              );
            })}
          </div>
        )}
        <div className="mt-auto flex flex-wrap items-center gap-3 pt-1">
          <Button type="submit" level={spec.danger ? "danger-filled" : "secondary"} busy={busy} busyText="Working…" disabled={disabled}>
            {spec.button}
          </Button>
        </div>
        <div aria-live="polite">
          {result && (result.ok || !result.field || !spec.fields.some((f) => f.name === result.field)) && (
            <InlineMessage kind={result.ok ? "success" : "error"}>
              <p className="whitespace-pre-wrap [overflow-wrap:anywhere]">{result.message}</p>
            </InlineMessage>
          )}
        </div>
      </form>
    </Slip>
  );
}

export function TestToolsPage() {
  const mode = useAdminRead<tools.TestMode>(tools.TEST_MODE_ADDRESS);
  const stages = Object.keys(STAGES).map(Number);
  const jump = (stage: number) => {
    const target = document.getElementById(`stage-${stage}`);
    if (!target) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    target.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
    target.querySelector<HTMLElement>("h2")?.focus({ preventScroll: true });
  };

  if (mode.loading) return <PageHeader title="Test tools" count="Checking test mode…" />;
  if (mode.error && mode.status !== 404) {
    return (
      <>
        <PageHeader title="Test tools" />
        <InlineMessage kind="error">{mode.error}</InlineMessage>
        <Button className="mt-3" onClick={mode.refresh}>Retry</Button>
      </>
    );
  }
  if (!mode.data?.adminTemporaryViewTestModeIsEnabled) {
    return (
      <>
        <PageHeader title="Test tools" count="Test mode is switched off, so there are no test tools." />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={
          <>
            <span>Test tools</span>
            <Flag family="waiting">Test mode</Flag>
          </>
        }
        count="These tools are enabled in test mode. Use them to create fixtures and check the results on the relevant pages."
      />
      <div className="xl:grid xl:grid-cols-[200px_minmax(0,1fr)] xl:gap-8">
        <div className="mb-5 xl:hidden">
          <label htmlFor="stagePicker" className="text-label font-semibold">
            Go to a stage
          </label>
          <SelectBox
            id="stagePicker"
            name="stage"
            value=""
            onChange={(v) => v && jump(Number(v))}
            options={[{ value: "", label: "Choose a stage" }, ...stages.map((stage) => ({ value: String(stage), label: `Stage ${stage}, ${STAGES[stage]}` }))]}
            className="mt-1 sm:max-w-[360px]"
          />
        </div>
        <nav aria-label="Stages" className="hidden xl:block">
          <ul className="flex flex-wrap gap-2 xl:sticky xl:top-[84px] xl:flex-col xl:gap-0.5">
            {stages.map((stage) => (
              <li key={stage}>
                <button
                  type="button"
                  onClick={() => jump(stage)}
                  className="flex min-h-10 w-full items-baseline gap-2 rounded-control border border-line bg-surface px-3 py-2.5 text-left text-label font-semibold hover:bg-hover xl:border-transparent xl:bg-transparent pointer-coarse:min-h-11"
                >
                  <span className="whitespace-nowrap">Stage {stage}</span>
                  <span className="hidden text-small font-normal text-ink-muted xl:inline">{STAGES[stage]}</span>
                </button>
              </li>
            ))}
          </ul>
        </nav>
        <div className="space-y-8">
          {stages.map((stage) => (
            <section key={stage} id={`stage-${stage}`} aria-labelledby={`stage-${stage}-title`} className="scroll-mt-20">
              <h2 id={`stage-${stage}-title`} tabIndex={-1} className="text-section-title font-[650] outline-none">
                Stage {stage}
              </h2>
              <p className="mb-3 text-ink-muted">{STAGES[stage]}</p>
              <div className="grid gap-5 lg:grid-cols-2">
                {TOOLS.filter((t) => t.stage === stage).map((spec) => (
                  <ToolCard key={spec.id} spec={spec} />
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </>
  );
}
