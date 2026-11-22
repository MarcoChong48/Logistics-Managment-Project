import { useRef, useState } from "react";
import { Download, Plus, Search, Users as UsersIcon } from "lucide-react";
import { parseTime, useAdminRead } from "../api/adminLists";
import { downloadCsv } from "../api/http";
import {
  changeUserRole,
  changeUserStatus,
  createUser,
  detailRow,
  emptyProfile,
  searchRow,
  userDetailAddress,
  userImpactAddress,
  usersAddress,
  type SearchUsers,
  type StatusChange,
  type UserDetail,
  type UserImpact,
  type UserRow,
  resetPassword,
  saveDeliveryProfile,
  unlockUser,
  workText,
  type TemporaryPasswordAnswer,
} from "../api/liveUsers";
import type { Role } from "../api/types";
import {
  Button,
  CellMain,
  EmptyState,
  FactList,
  Flag,
  InlineMessage,
  MessagePlace,
  Note,
  PageHeader,
  Slip,
  SlipStrip,
  StatusBadge,
  TimeText,
  type MessageKind,
} from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { Pager } from "../components/Pager";
import { ActionsMenu } from "../components/ActionsMenu";
import { ConfirmDialog, afterClose } from "../components/ConfirmDialog";
import { Dialog } from "../components/Dialog";
import { CheckboxField, ReasonField, SelectBox, SelectField, TextAreaField, TextField, inputClass, reasonReady } from "../components/form";
import { useToast } from "../components/Toast";
import { useListParams } from "../lib/useListParams";
import { formatTime, plural } from "../lib/format";
import { accountStatusLook, roleLabel } from "../lib/status";
import { cn } from "../lib/cn";
import { useFocusFirstInvalid } from "../lib/useFocusFirstInvalid";

type Kind = "view" | "role" | "reset" | "ban" | "unban" | "deactivate" | "reactivate" | "unlock";
type Open = { kind: Kind; row: UserRow } | { kind: "create" } | { kind: "password"; answer: TemporaryPasswordAnswer } | null;

const ROLE_OPTIONS = [
  { value: "", label: "All roles" },
  { value: "CLIENT", label: "Client" },
  { value: "WAREHOUSE_WORKER", label: "Warehouse Worker" },
  { value: "DELIVERY_WORKER", label: "Delivery Worker" },
  { value: "ADMIN", label: "Admin" },
];
const STATUS_OPTIONS = [
  { value: "", label: "All statuses" },
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Deactivated" },
  { value: "BANNED", label: "Banned" },
  { value: "LOCKED_NOW", label: "Locked now" },
];

function StatusCell({ row }: { row: UserRow }) {
  const u = row;
  return (
    <CellMain>
      {u.accountStatus === "ACTIVE" && row.lockMinutesLeft === null ? (
        <span className="text-ink-muted">Active</span>
      ) : (
        <StatusBadge look={accountStatusLook(u.accountStatus, row.lockMinutesLeft)} />
      )}
      {row.isSuperAdmin && <Flag family="closed">Super admin</Flag>}
      {u.mustChangePassword && <Flag family="closed">Temporary password</Flag>}
      {u.failedLoginCount > 0 && u.accountStatus !== "LOCKED" && <span className="text-small text-ink-muted">{u.failedLoginCount} of 5 tries</span>}
    </CellMain>
  );
}

export function UsersPage() {
  const toast = useToast();
  const { values, set, clear } = useListParams({ q: "", role: "", status: "", page: "1" });
  const [open, setOpen] = useState<Open>(null);
  const passwordOpener = useRef<HTMLElement | null>(null);
  const [downloading, setDownloading] = useState(false);
  const { data, error, loading, refresh } = useAdminRead<SearchUsers>(usersAddress(values, Number(values.page) || 1));
  const rows = data?.adminSearchUserRowList.map(searchRow) ?? [];
  const meIsSuper = data?.adminSearchUserCanManageAdminAccount ?? false;
  const filtered = Boolean(values.q || values.role || values.status);

  const download = async () => {
    setDownloading(true);
    try {
      await downloadCsv(usersAddress(values), "users.csv");
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "Could not download users.");
    } finally {
      setDownloading(false);
    }
  };

  const menu = (row: UserRow) => {
    const u = row;
    const go = (kind: Kind) => () => {
      if (kind === "reset") passwordOpener.current = document.activeElement as HTMLElement | null;
      setOpen({ kind, row });
    };
    if (!row.canChange) return { lines: [{ label: "View", onSelect: go("view") }], danger: [] };
    const lines = [{ label: "View", onSelect: go("view") }];
    if (u.role !== "CLIENT" && u.accountStatus === "ACTIVE") lines.push({ label: "Change role", onSelect: go("role") });
    if (u.accountStatus === "ACTIVE") lines.push({ label: "Reset password", onSelect: go("reset") });
    if (row.isLockedNow || u.accountStatus === "LOCKED") lines.push({ label: "Unlock", onSelect: go("unlock") });
    if (u.accountStatus === "BANNED") lines.push({ label: "Unban", onSelect: go("unban") });
    if (u.accountStatus === "INACTIVE") lines.push({ label: "Reactivate", onSelect: go("reactivate") });
    const danger = [];
    if (u.accountStatus === "ACTIVE") danger.push({ label: "Ban", onSelect: go("ban") });
    if (u.accountStatus === "ACTIVE" || u.accountStatus === "BANNED") danger.push({ label: "Deactivate", onSelect: go("deactivate") });
    return { lines, danger };
  };

  const columns: Column<UserRow>[] = [
    {
      key: "username",
      title: "Username",
      mobile: "title",
      cell: (r) => (
        <span className="font-semibold">
          {r.username}
          {r.isOwnAccount && <span className="font-normal text-ink-muted"> (you)</span>}
        </span>
      ),
    },
    { key: "role", title: "Role", cell: (r) => roleLabel(r.role) },
    { key: "status", title: "Status", cell: (r) => <StatusCell row={r} /> },
    { key: "login", title: "Last login", cell: (r) => <TimeText time={r.lastLoginTime} /> },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      cell: (r) => {
        if (r.isOwnAccount) return <span className="text-small text-ink-muted">Your own account</span>;
        const m = menu(r);
        return <ActionsMenu label={`Actions for ${r.username}`} lines={m.lines} dangerLines={m.danger} />;
      },
    },
  ];

  const target = open && "row" in open ? open.row : null;
  const done = (kind: MessageKind, message: string) => {
    refresh();
    setOpen(null);
    afterClose(() => toast.show(kind, message));
  };
  const showTemporaryPassword = (answer: TemporaryPasswordAnswer) => {
    refresh();
    setOpen({ kind: "password", answer });
  };

  return (
    <>
      <PageHeader
        title="Users"
        count={data ? plural(data.adminSearchUserTotalRowCount, filtered ? "matching account" : "account") : undefined}
        actions={
          <>
            <Button icon={<Download aria-hidden="true" className="size-4" />} onClick={download} busy={downloading} busyText="Downloading…" disabled={!data}>
              Download CSV
            </Button>
            <Button level="main" icon={<Plus aria-hidden="true" className="size-4" />} onClick={(event) => {
              passwordOpener.current = event.currentTarget;
              setOpen({ kind: "create" });
            }} disabled={!data}>
              Create user
            </Button>
          </>
        }
      />
      <Slip as="div">
        <div role="search" aria-label="Filter users" className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3 md:px-5">
          <span className="relative w-full sm:w-[260px]">
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
            <input type="search" name="q" autoComplete="off" spellCheck={false} aria-label="Search username" placeholder="Search username…" value={values.q} onChange={(e) => set({ q: e.target.value })} className={cn(inputClass, "pl-9")} />
          </span>
          <SelectBox aria-label="Role" name="role" value={values.role} onChange={(v) => set({ role: v })} options={ROLE_OPTIONS} className="w-[calc(50%-4px)] sm:w-[190px]" />
          <SelectBox aria-label="Status" name="status" value={values.status} onChange={(v) => set({ status: v })} options={STATUS_OPTIONS} className="w-[calc(50%-4px)] sm:w-[170px]" />
          {filtered && (
            <Button level="quiet" small onClick={clear}>
              Clear
            </Button>
          )}
        </div>
        {data && !meIsSuper && (
          <SlipStrip>
            <Note>Only the super admin can create or change Admin accounts.</Note>
          </SlipStrip>
        )}
        <DataTable
          label="Users table"
          columns={columns}
          rows={rows}
          rowKey={(r) => r.userID}
          empty={
            loading ? <p className="p-4 text-small text-ink-muted" role="status">Loading users…</p> :
            error ? <div className="space-y-2 p-4"><InlineMessage kind="error">{error}</InlineMessage><Button small onClick={refresh}>Retry</Button></div> :
            <EmptyState
              icon={<UsersIcon aria-hidden="true" className="size-7" />}
              title="No accounts match these filters."
              action={
                <Button small level="quiet" onClick={clear}>
                  Clear filters
                </Button>
              }
            />
          }
        />
        {data && data.adminSearchUserTotalPageCount > 1 && <Pager label="Users pages" page={data.adminSearchUserPageNumber} pageCount={data.adminSearchUserTotalPageCount} onPage={(n) => set({ page: String(n) })} />}
      </Slip>

      {open?.kind === "create" && <CreateUserDialog meIsSuper={meIsSuper} onClose={() => setOpen(null)} onCreated={showTemporaryPassword} />}
      {open?.kind === "password" && <TemporaryPasswordDialog answer={open.answer} returnFocus={() => passwordOpener.current} onClose={() => setOpen(null)} />}
      {open?.kind === "view" && target && <LoadUserDetails row={target} onClose={() => setOpen(null)} onSaved={(m) => done("success", m)} />}
      {open?.kind === "role" && target && <ChangeRoleDialog user={target} meIsSuper={meIsSuper} onClose={() => setOpen(null)} onDone={(m) => done("success", m)} />}
      {open?.kind === "reset" && target && (
        <ResetPasswordDialog user={target} onClose={() => setOpen(null)} onDone={showTemporaryPassword} />
      )}
      {open && (open.kind === "ban" || open.kind === "unban" || open.kind === "deactivate" || open.kind === "reactivate") && target && (
        <StatusDialog kind={open.kind} user={target} onClose={() => setOpen(null)} onDone={(m) => done("success", m)} />
      )}
      {open?.kind === "unlock" && target && (
        <ConfirmDialog
          title={`Unlock ${target.username}?`}
          confirmLabel="Unlock user"
          busyLabel="Unlocking"
          onClose={() => setOpen(null)}
          onConfirm={() => unlockUser(target.userID)}
          onSuccess={(r) => done("success", r.message)}
        >
          <p>The lock is removed and the count of wrong passwords is set back to 0. The user can log in at once.</p>
        </ConfirmDialog>
      )}
    </>
  );
}

// ----- windows ----------------------------------------------------------------------------------

function roleOptions(meIsSuper: boolean, except?: Role) {
  return (["CLIENT", "WAREHOUSE_WORKER", "DELIVERY_WORKER", "ADMIN"] as Role[])
    .filter((r) => r !== except && (r !== "ADMIN" || meIsSuper))
    .map((r) => ({ value: r, label: roleLabel(r) }));
}

function CreateUserDialog({ meIsSuper, onClose, onCreated }: { meIsSuper: boolean; onClose: () => void; onCreated: (answer: TemporaryPasswordAnswer) => void }) {
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<Role>("CLIENT");
  const [profile, setProfile] = useState(emptyProfile);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ field?: string; text: string } | null>(null);
  const fieldError = (name: string) => (error?.field === name ? error.text : null);

  useFocusFirstInvalid(error);

  const confirm = async () => {
    if (busy || !username.trim()) return;
    setBusy(true);
    setError(null);
    const answer = await createUser({ username, role, ...profile });
    setBusy(false);
    if (answer.ok && answer.data) onCreated(answer.data);
    else setError({ field: answer.field, text: answer.message });
  };
  const close = () => {
    if (!busy) onClose();
  };

  return (
    <Dialog
      title="Create user"
      onClose={close}
      footer={
        <>
          <Button onClick={close} disabled={busy}>Cancel</Button>
          <Button level="main" disabled={!username.trim()} busy={busy} busyText="Creating…" onClick={confirm}>
            Create user
          </Button>
        </>
      }
    >
      <TextField label="Username" value={username} onChange={setUsername} autoCapitalize="none" help="4 to 30 characters: letters, digits and underscore." error={fieldError("username")} autoComplete="off" spellCheck={false} />
      <SelectField label="Role" value={role} onChange={(v) => setRole(v as Role)} options={roleOptions(meIsSuper)} error={fieldError("role")} />
      {role === "DELIVERY_WORKER" && (
        <fieldset className="space-y-4 rounded-control border border-line px-4 pb-4 pt-2">
          <legend className="px-1 text-label font-semibold">Delivery Worker profile</legend>
          <TextField label="Employee number" optional spellCheck={false} value={profile.employeeNumber} onChange={(v) => setProfile({ ...profile, employeeNumber: v })} error={fieldError("employeeNumber")} />
          <TextField label="Vehicle type" optional value={profile.vehicleType} onChange={(v) => setProfile({ ...profile, vehicleType: v })} error={fieldError("vehicleType")} />
          <TextField label="Vehicle plate" optional spellCheck={false} value={profile.vehiclePlate} onChange={(v) => setProfile({ ...profile, vehiclePlate: v })} error={fieldError("vehiclePlate")} />
          <TextAreaField label="Equipment note" optional value={profile.equipmentNote} onChange={(v) => setProfile({ ...profile, equipmentNote: v })} error={fieldError("equipmentNote")} />
        </fieldset>
      )}
      <p className="text-small text-ink-muted">The new account gets a temporary password that is valid for 24 hours. It is shown once, in the next window.</p>
      <MessagePlace message={error && !error.field ? { kind: "error", text: error.text } : null} />
    </Dialog>
  );
}

function TemporaryPasswordDialog({ answer, onClose, returnFocus }: { answer: TemporaryPasswordAnswer; onClose: () => void; returnFocus: () => HTMLElement | null }) {
  const [copyMessage, setCopyMessage] = useState<{ kind: MessageKind; text: string } | null>(null);
  const attempt = useRef(0);
  const copy = async () => {
    attempt.current += 1;
    const mine = attempt.current;
    setCopyMessage(null);
    let copied = false;
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(answer.temporaryPassword);
        copied = true;
      }
    } catch {
      copied = false;
    }
    if (mine !== attempt.current) return;
    setCopyMessage(copied ? { kind: "success", text: "Copied." } : { kind: "error", text: "Could not copy. Select the password and copy it by hand." });
  };
  return (
    <Dialog
      title={`Temporary password for ${answer.username}`}
      onClose={onClose}
      returnFocus={returnFocus}
      footer={
        <Button level="main" onClick={onClose}>
          Close
        </Button>
      }
    >
      <p>Give this password to the user. The user must change it at the first login.</p>
      <div className="flex flex-wrap items-center gap-3 rounded-control border border-line bg-sunken px-4 py-3">
        <span id="temporaryPasswordValue" className="flex-1 select-all font-mono text-[20px] font-semibold tracking-[0.04em]">
          {answer.temporaryPassword}
        </span>
        <Button small onClick={copy}>
          Copy
        </Button>
      </div>
      <MessagePlace message={copyMessage} />
      <p className="text-small text-ink-muted">
        Valid until <TimeText time={answer.validUntil} />. It is shown only once.
      </p>
    </Dialog>
  );
}

function LoadUserDetails({ row, onClose, onSaved }: { row: UserRow; onClose: () => void; onSaved: (message: string) => void }) {
  const { data, error, refresh } = useAdminRead<UserDetail>(userDetailAddress(row.userID));
  const [opener] = useState(() => document.activeElement as HTMLElement | null);
  if (data) return <ViewUserDialog detail={data} user={detailRow(data, row.lastLoginTime)} onClose={onClose} onSaved={onSaved} returnFocus={() => opener} />;
  return (
    <Dialog title={`User ${row.username}`} onClose={onClose} footer={<Button onClick={onClose}>Close</Button>}>
      {error ? <><InlineMessage kind="error">{error}</InlineMessage><Button small onClick={refresh}>Retry</Button></> : <p role="status">Loading user details…</p>}
    </Dialog>
  );
}

function ViewUserDialog({ detail, user: u, onClose, onSaved, returnFocus }: { detail: UserDetail; user: UserRow; onClose: () => void; onSaved: (message: string) => void; returnFocus: () => HTMLElement | null }) {
  const { data: impact, error: impactError } = useAdminRead<UserImpact>(u.role === "ADMIN" ? null : userImpactAddress(u.userID));
  const [profile, setProfile] = useState(() => emptyProfile(detail));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ field?: string; text: string } | null>(null);
  const isDriver = u.role === "DELIVERY_WORKER";
  const canEdit = isDriver && u.canChange;
  const expiryTime = parseTime(detail.temporaryPasswordExpiryTime);
  const temporary = !u.mustChangePassword ? "Not in use" :
    expiryTime === null ? "Password change required" :
    expiryTime < Date.now() ? `Ran out ${formatTime(expiryTime)}` :
    `In use, valid until ${formatTime(expiryTime)}`;
  const work: Array<[string, string]> = [];
  if (impact && u.role === "WAREHOUSE_WORKER") {
    work.push(["Active picking tasks", workText(impact) || "None"]);
  }
  if (impact && isDriver) {
    work.push(["Held packages", impact.adminPreviewUserImpactHeldPackageOrderReferenceList.join(", ") || String(impact.adminPreviewUserImpactHeldPackageCount)]);
    work.push(["Open pickup lists", String(impact.adminPreviewUserImpactOpenPickupListCount)]);
  }
  if (impact && u.role === "CLIENT") work.push(["Open orders", String(impact.adminPreviewUserImpactOpenClientOrderCount)]);

  useFocusFirstInvalid(error);
  const save = async () => {
    if (busy || !canEdit) return;
    setBusy(true);
    setError(null);
    const answer = await saveDeliveryProfile(u.userID, profile);
    setBusy(false);
    if (answer.ok) onSaved(answer.message);
    else setError({ field: answer.field, text: answer.message });
  };
  const close = () => {
    if (!busy) onClose();
  };
  const fieldError = (name: string) => (error?.field === name ? error.text : null);

  return (
    <Dialog
      title={`User ${u.username}`}
      wide
      onClose={close}
      returnFocus={returnFocus}
      footer={
        <>
          <Button onClick={close} disabled={busy}>Close</Button>
          {canEdit && (
            <Button level="main" busy={busy} busyText="Saving…" onClick={save}>
              Save profile
            </Button>
          )}
        </>
      }
    >
      <FactList
        columns={2}
        facts={[
          ["Username", u.username],
          ["Role", roleLabel(u.role)],
          ["Status", <StatusCell key="s" row={u} />],
          ["Created", <TimeText key="c" time={u.createdTime} />],
          ["Last login", <TimeText key="l" time={u.lastLoginTime} />],
          ["Wrong password tries", `${u.failedLoginCount} of 5`],
          ["Temporary password", temporary],
          ...work,
        ]}
      />
      {impactError && <InlineMessage kind="error">{impactError}</InlineMessage>}
      {isDriver && (
        <fieldset className="space-y-4 rounded-control border border-line px-4 pb-4 pt-2" disabled={!canEdit}>
          <legend className="px-1 text-label font-semibold">Delivery Worker profile</legend>
          <TextField label="Employee number" optional spellCheck={false} value={profile.employeeNumber} onChange={(v) => setProfile({ ...profile, employeeNumber: v })} error={fieldError("employeeNumber")} />
          <TextField label="Vehicle type" optional value={profile.vehicleType} onChange={(v) => setProfile({ ...profile, vehicleType: v })} error={fieldError("vehicleType")} />
          <TextField label="Vehicle plate" optional spellCheck={false} value={profile.vehiclePlate} onChange={(v) => setProfile({ ...profile, vehiclePlate: v })} error={fieldError("vehiclePlate")} />
          <TextAreaField label="Equipment note" optional value={profile.equipmentNote} onChange={(v) => setProfile({ ...profile, equipmentNote: v })} error={fieldError("equipmentNote")} />
        </fieldset>
      )}
      <MessagePlace message={error && !error.field ? { kind: "error", text: error.text } : null} />
    </Dialog>
  );
}

function ChangeRoleDialog({ user: u, meIsSuper, onClose, onDone }: { user: UserRow; meIsSuper: boolean; onClose: () => void; onDone: (message: string) => void }) {
  const { data: impact, error, loading, refresh } = useAdminRead<UserImpact>(userImpactAddress(u.userID));
  const options = roleOptions(meIsSuper, u.role).filter((o) => o.value !== "CLIENT");
  const [role, setRole] = useState<string>(options[0]?.value ?? "");
  const [reason, setReason] = useState("");
  return (
    <ConfirmDialog
      title={`Change role of ${u.username}`}
      confirmLabel="Change role"
      busyLabel="Changing"
      canConfirm={reasonReady(reason) && Boolean(impact && !impact.adminPreviewUserImpactHasActiveWork && role)}
      onClose={onClose}
      onConfirm={() => changeUserRole(u.userID, role as Role, reason)}
      onSuccess={(r) => onDone(r.message)}
    >
      <FactList facts={[["Current role", roleLabel(u.role)]]} />
      <SelectField label="New role" value={role} onChange={setRole} options={options} />
      <ReasonField value={reason} onChange={setReason} />
      {loading && <p role="status" className="text-small text-ink-muted">Checking active work…</p>}
      {error && <><InlineMessage kind="error">{error}</InlineMessage><Button small onClick={refresh}>Retry</Button></>}
      {impact?.adminPreviewUserImpactHasActiveWork ? (
        <InlineMessage kind="error">This user still has work: {workText(impact)}. Reassign it on Warehouse cases or Delivery cases first.</InlineMessage>
      ) : impact && (
        <p className="text-small text-ink-muted">This user has no active work. After the change the user must log in again.</p>
      )}
    </ConfirmDialog>
  );
}

function ResetPasswordDialog({ user: u, onClose, onDone }: { user: UserRow; onClose: () => void; onDone: (answer: TemporaryPasswordAnswer) => void }) {
  const [tick, setTick] = useState(false);
  const [method, setMethod] = useState("");
  return (
    <ConfirmDialog
      title={`Reset password of ${u.username}`}
      confirmLabel="Reset password"
      busyLabel="Resetting"
      canConfirm={tick && reasonReady(method)}
      onClose={onClose}
      onConfirm={() => resetPassword(u.userID, tick, method)}
      onSuccess={(r) => r.data && onDone(r.data)}
    >
      <p>The user gets a new temporary password and must change it at the next login. A lock on the account is removed.</p>
      <CheckboxField label="I have checked who this person is" checked={tick} onChange={setTick} />
      <TextField label="How was the person checked?" value={method} onChange={setMethod} help="At least 5 characters, for example: staff card shown in person." autoComplete="off" />
    </ConfirmDialog>
  );
}

function StatusDialog({ kind, user: u, onClose, onDone }: { kind: StatusChange; user: UserRow; onClose: () => void; onDone: (message: string) => void }) {
  const needsImpact = kind === "ban" || kind === "deactivate";
  const { data: impact, error, loading, refresh } = useAdminRead<UserImpact>(needsImpact ? userImpactAddress(u.userID) : null);
  const [reason, setReason] = useState("");
  const text = {
    ban: { title: `Ban ${u.username}?`, body: "The user is logged out at once and cannot log in again until an Admin lifts the ban.", button: "Ban user", level: "danger-filled" as const },
    unban: { title: `Unban ${u.username}?`, body: "The user can log in again. The count of wrong passwords is set back to 0.", button: "Unban user", level: "main" as const },
    deactivate: {
      title: `Deactivate ${u.username}?`,
      body: "The account is switched off and the user is logged out at once. The account and its history are kept. An Admin can reactivate it later.",
      button: "Deactivate user",
      level: "danger-filled" as const,
    },
    reactivate: { title: `Reactivate ${u.username}?`, body: "The account is switched on again and the user can log in.", button: "Reactivate user", level: "main" as const },
  }[kind];
  const showWork = impact?.adminPreviewUserImpactHasActiveWork;
  const openOrders = impact?.adminPreviewUserImpactOpenClientOrderCount ?? 0;
  return (
    <ConfirmDialog
      title={text.title}
      confirmLabel={text.button}
      busyLabel="Saving…"
      level={text.level}
      canConfirm={reasonReady(reason) && (!needsImpact || Boolean(impact))}
      onClose={onClose}
      onConfirm={() => changeUserStatus(u.userID, kind, reason)}
      onSuccess={(r) => onDone(r.message)}
    >
      <p>{text.body}</p>
      {loading && <p role="status" className="text-small text-ink-muted">Checking active work…</p>}
      {error && <><InlineMessage kind="error">{error}</InlineMessage><Button small onClick={refresh}>Retry</Button></>}
      {showWork && impact && <p className="rounded-control bg-waiting-bg px-3.5 py-2.5 text-small text-waiting-fg">This user still has work: {workText(impact)}.</p>}
      {kind === "deactivate" && u.role === "CLIENT" && openOrders > 0 && <p className="rounded-control bg-sunken px-3.5 py-2.5 text-small">This client has {plural(openOrders, "open order")}.</p>}
      <ReasonField value={reason} onChange={setReason} />
    </ConfirmDialog>
  );
}
