import { useState, type ChangeEvent } from "react";
import { Download, PackageSearch, Plus, Printer, Search, Upload } from "lucide-react";
import { useAdminRead } from "../api/adminLists";
import { downloadCsv } from "../api/http";
import { IMPORT_HEADER, createProduct, editProduct, importProducts, productImpactAddress, productQrAddress, productsAddress, setProductListing, type ImportAnswer, type Product, type ProductImpact, type SearchProducts } from "../api/liveProducts";
import { Button, CellMain, EmptyState, Flag, InlineMessage, MessagePlace, Note, PageHeader, Slip, SlipStrip, StatusBadge } from "../components/ui";
import { DataTable, type Column } from "../components/DataTable";
import { Pager } from "../components/Pager";
import { ActionsMenu } from "../components/ActionsMenu";
import { ConfirmDialog, afterClose } from "../components/ConfirmDialog";
import { Dialog } from "../components/Dialog";
import { CheckboxField, ReasonField, SelectBox, SelectField, TextField, inputClass, reasonReady } from "../components/form";
import { useToast } from "../components/Toast";
import { useListParams } from "../lib/useListParams";
import { downloadCSV } from "../lib/csv";
import { formatMoney, plural } from "../lib/format";
import { cn } from "../lib/cn";
import { useFocusFirstInvalid } from "../lib/useFocusFirstInvalid";

type Open = { kind: "add" } | { kind: "import" } | { kind: "edit" | "qr" | "deactivate" | "activate"; product: Product } | null;

const STATUS_OPTIONS = [
  { value: "", label: "All statuses" },
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" },
];

export function ProductsPage() {
  const toast = useToast();
  const { values, set, clear } = useListParams({ q: "", status: "", low: "", page: "1" });
  const [open, setOpen] = useState<Open>(null);
  const [downloading, setDownloading] = useState(false);
  const { data, loading, error, refresh } = useAdminRead<SearchProducts>(productsAddress(values, Number(values.page) || 1));
  const rows = data?.adminSearchProductRowList ?? [];
  const filtered = Boolean(values.q || values.status || values.low);

  const download = async () => {
    setDownloading(true);
    try {
      await downloadCsv(productsAddress(values), "products.csv");
    } catch (error) {
      toast.show("error", error instanceof Error ? error.message : "Could not download products.");
    } finally {
      setDownloading(false);
    }
  };

  const done = (message: string) => {
    refresh();
    setOpen(null);
    afterClose(() => toast.show("success", message));
  };

  const columns: Column<Product>[] = [
    { key: "sku", title: "SKU", mobile: "title", cell: (r) => <span className="whitespace-nowrap font-semibold tabular">{r.SKU}</span> },
    {
      key: "name",
      title: "Name",
      mobile: "wide",
      className: "min-w-[176px]",
      cell: (r) => (
        <CellMain>
          <span>{r.name}</span>
          {r.adminSearchProductIsLowStock && <Flag family="waiting">Low stock</Flag>}
          {r.adminSearchProductReservationShortfall > 0 && <Flag family="problem">Shortfall {r.adminSearchProductReservationShortfall}</Flag>}
        </CellMain>
      ),
    },
    { key: "price", title: "Price", numeric: true, cell: (r) => formatMoney(r.currentPrice) },
    { key: "physical", title: "Physical", numeric: true, cell: (r) => r.physicalStock },
    { key: "reserved", title: "Reserved", numeric: true, cell: (r) => r.reservedStock },
    { key: "unavailable", title: "Unavailable", numeric: true, cell: (r) => r.adminSearchProductUnavailableStock },
    { key: "available", title: "Available", numeric: true, cell: (r) => <span className={cn("font-semibold", r.adminSearchProductIsLowStock && "text-terracotta")}>{r.adminSearchProductAvailableStock}</span> },
    { key: "low", title: "Low below", numeric: true, cell: (r) => r.lowStockThreshold },
    {
      key: "status",
      title: "Status",
      mobile: "aside",
      cell: (r) => (r.listingStatus === "ACTIVE" ? <span className="text-ink-muted">Active</span> : <StatusBadge word={r.listingStatus} />),
    },
    {
      key: "actions",
      title: "Actions",
      mobile: "actions",
      cell: (r) => {
        const active = r.listingStatus === "ACTIVE";
        const lines = [
          { label: "Edit", onSelect: () => setOpen({ kind: "edit", product: r }) },
          { label: "Show QR code", onSelect: () => setOpen({ kind: "qr", product: r }) },
        ];
        if (!active) lines.push({ label: "Activate", onSelect: () => setOpen({ kind: "activate", product: r }) });
        return <ActionsMenu label={`Actions for ${r.SKU}`} lines={lines} dangerLines={active ? [{ label: "Deactivate", onSelect: () => setOpen({ kind: "deactivate", product: r }) }] : []} />;
      },
    },
  ];

  const product = open && "product" in open ? open.product : null;

  return (
    <>
      <PageHeader
        title="Products"
        count={data ? plural(data.adminSearchProductTotalRowCount, filtered ? "matching product" : "product") : undefined}
        actions={
          <>
            <Button icon={<Download aria-hidden="true" className="size-4" />} onClick={download} busy={downloading} disabled={!data} busyText="Downloading…">
              Download CSV
            </Button>
            <Button icon={<Upload aria-hidden="true" className="size-4" />} onClick={() => setOpen({ kind: "import" })}>
              Import CSV
            </Button>
            <Button level="main" icon={<Plus aria-hidden="true" className="size-4" />} onClick={() => setOpen({ kind: "add" })}>
              Add product
            </Button>
          </>
        }
      />
      <Slip as="div">
        <div role="search" aria-label="Filter products" className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3 md:px-5">
          <span className="relative w-full sm:w-[280px]">
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
            <input type="search" name="q" autoComplete="off" spellCheck={false} aria-label="Search SKU or name" placeholder="Search SKU or name…" value={values.q} onChange={(e) => set({ q: e.target.value })} className={cn(inputClass, "pl-9")} />
          </span>
          <SelectBox aria-label="Status" name="status" value={values.status} onChange={(v) => set({ status: v })} options={STATUS_OPTIONS} className="w-[calc(50%-4px)] sm:w-[170px]" />
          <CheckboxField label="Low stock only" checked={values.low === "true"} onChange={(checked) => set({ low: checked ? "true" : "" })} className="px-1" />
          {filtered && (
            <Button level="quiet" small onClick={clear}>
              Clear
            </Button>
          )}
        </div>
        <SlipStrip>
          <Note>Stock is changed by Warehouse. Available is physical minus reserved and unavailable stock, with a minimum of 0.</Note>
        </SlipStrip>
        <DataTable
          label="Products table"
          columns={columns}
          rows={rows}
          rowKey={(r) => r.productID}
          minWidth="min-w-[940px]"
          empty={
            loading ? <p className="p-4 text-small text-ink-muted" role="status">Loading products…</p> :
            error ? <div className="space-y-2 p-4"><InlineMessage kind="error">{error}</InlineMessage><Button small onClick={refresh}>Retry</Button></div> :
            <EmptyState
              icon={<PackageSearch aria-hidden="true" className="size-7" />}
              title="No products match these filters."
              action={
                <Button small level="quiet" onClick={clear}>
                  Clear filters
                </Button>
              }
            />
          }
        />
        {data && data.adminSearchProductTotalPageCount > 1 && <Pager label="Products pages" page={data.adminSearchProductPageNumber} pageCount={data.adminSearchProductTotalPageCount} onPage={(n) => set({ page: String(n) })} />}
      </Slip>

      {open?.kind === "add" && <ProductFormDialog onClose={() => setOpen(null)} onDone={done} />}
      {open?.kind === "edit" && product && <ProductFormDialog product={product} onClose={() => setOpen(null)} onDone={done} />}
      {open?.kind === "qr" && product && <QRDialog product={product} onClose={() => setOpen(null)} />}
      {open?.kind === "import" && <ImportDialog onClose={() => setOpen(null)} onImported={refresh} />}
      {open?.kind === "deactivate" && product && <DeactivateDialog product={product} onClose={() => setOpen(null)} onDone={done} />}
      {open?.kind === "activate" && product && (
        <ConfirmDialog
          title={`Activate ${product.SKU}?`}
          confirmLabel="Activate product"
          busyLabel="Activating"
          onClose={() => setOpen(null)}
          onConfirm={() => setProductListing(product.productID, "ACTIVE", "")}
          onSuccess={(r) => done(r.message)}
        >
          <p>Clients can order this product again.</p>
        </ConfirmDialog>
      )}
    </>
  );
}

function ProductFormDialog({ product, onClose, onDone }: { product?: Product; onClose: () => void; onDone: (message: string) => void }) {
  const [form, setForm] = useState({
    SKU: product?.SKU ?? "",
    name: product?.name ?? "",
    price: product ? product.currentPrice.toFixed(2) : "",
    lowBelow: product ? String(product.lowStockThreshold) : "",
    status: (product?.listingStatus ?? "ACTIVE") as "ACTIVE" | "INACTIVE",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ field?: string; text: string } | null>(null);
  const fieldError = (name: string) => (error?.field === name ? error.text : null);
  const update = (patch: Partial<typeof form>) => {
    setForm({ ...form, ...patch });
    setError(null);
  };
  useFocusFirstInvalid(error);
  const confirm = async () => {
    if (busy) return;
    setBusy(true);
    const answer = product ? await editProduct(product.productID, form) : await createProduct(form);
    setBusy(false);
    if (answer.ok) onDone(answer.message);
    else setError({ field: answer.field, text: answer.message });
  };
  const close = () => {
    if (!busy) onClose();
  };
  return (
    <Dialog
      title={product ? `Edit product ${product.SKU}` : "Add product"}
      onClose={close}
      footer={
        <>
          <Button onClick={close} disabled={busy}>Cancel</Button>
          <Button level="main" busy={busy} busyText="Saving…" disabled={!form.SKU.trim() || !form.name.trim() || !form.price.trim() || !form.lowBelow.trim()} onClick={confirm}>
            {product ? "Save product" : "Add product"}
          </Button>
        </>
      }
    >
      <TextField
        label="SKU"
        value={form.SKU}
        onChange={(v) => update({ SKU: v })}
        help={product ? "Printed QR labels must be printed again after the SKU changes." : "3 to 60 characters: letters, digits and hyphens. Small letters become capital letters."}
        error={fieldError("SKU")}
        autoComplete="off"
        spellCheck={false}
      />
      <TextField label="Name" value={form.name} onChange={(v) => update({ name: v })} help={product ? undefined : "2 to 200 characters."} error={fieldError("name")} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Price" value={form.price} onChange={(v) => update({ price: v })} inputMode="decimal" help="In HKD. Above 0, with at most 2 decimal places." error={fieldError("price")} placeholder="e.g. 45.00" />
        <TextField
          label="Low below"
          value={form.lowBelow}
          onChange={(v) => update({ lowBelow: v })}
          inputMode="numeric"
          help="Low when available stock is below this number. 0 means never low."
          error={fieldError("lowBelow")}
          placeholder="e.g. 5"
        />
      </div>
      {!product && (
        <>
          <SelectField
            label="Status"
            value={form.status}
            onChange={(v) => update({ status: v as "ACTIVE" | "INACTIVE" })}
            options={[
              { value: "ACTIVE", label: "Active" },
              { value: "INACTIVE", label: "Inactive" },
            ]}
          />
          <p className="text-small text-ink-muted">A new product starts with stock 0. Stock is changed only by Warehouse.</p>
        </>
      )}
      <MessagePlace message={error && !error.field ? { kind: "error", text: error.text } : null} />
    </Dialog>
  );
}

function QRDialog({ product, onClose }: { product: Product; onClose: () => void }) {
  const [imageStatus, setImageStatus] = useState<"loading" | "ready" | "error">("loading");
  const print = () => {
    const root = document.documentElement;
    root.classList.add("lms-print-label");
    const cleanUp = () => {
      root.classList.remove("lms-print-label");
      window.removeEventListener("afterprint", cleanUp);
    };
    window.addEventListener("afterprint", cleanUp);
    window.print();
  };
  return (
    <Dialog
      title={`QR label of ${product.SKU}`}
      onClose={onClose}
      footer={
        <>
          <Button icon={<Printer aria-hidden="true" className="size-4" />} onClick={print} disabled={imageStatus !== "ready"}>
            Print label
          </Button>
          <Button level="main" onClick={onClose}>
            Close
          </Button>
        </>
      }
    >
      <div className="lms-print-area">
        <div className="mx-auto flex w-full max-w-[300px] flex-col items-center gap-3 rounded-card border-2 border-ink px-6 py-6 text-center">
          <img className="size-44" src={productQrAddress(product.productID)} alt={`QR code for SKU ${product.SKU}`} onLoad={() => setImageStatus("ready")} onError={() => setImageStatus("error")} />
          <p className="text-[20px] font-bold leading-7 tabular">{product.SKU}</p>
          <p className="text-body">{product.name}</p>
        </div>
      </div>
      {imageStatus === "error" && <InlineMessage kind="error">Could not load the QR code. Close this window and try again.</InlineMessage>}
      <p className="text-small text-ink-muted">The QR code holds exactly the SKU, so a scanner reads {product.SKU}.</p>
    </Dialog>
  );
}

const EXAMPLE_FILE = `${IMPORT_HEADER}\r\nSKU-2001,Linen bread bag,68.00,5\r\nSKU-2002,Glass oil bottle 500 ml,92.50,4\r\nSKU-1001,Stoneware mug 350 ml oat,80.00,3\r\nSKU 2004,Cotton napkin,twelve,2\r\n`;

function ImportDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ message: string; data: ImportAnswer } | null>(null);
  const choose = (event: ChangeEvent<HTMLInputElement>) => {
    setResult(null);
    setError(null);
    setFile(event.target.files?.[0] ?? null);
  };
  const run = async () => {
    if (!file || busy) return;
    setBusy(true);
    setResult(null);
    setError(null);
    const answer = await importProducts(file);
    setBusy(false);
    if (answer.ok && answer.data) {
      setResult({ message: answer.message, data: answer.data });
      onImported();
    } else setError(answer.message);
  };
  const close = () => {
    if (!busy) onClose();
  };
  return (
    <Dialog
      title="Import products from a CSV file"
      wide
      onClose={close}
      footer={
        <>
          <Button onClick={close} disabled={busy}>Close</Button>
          <Button level="main" disabled={!file} busy={busy} busyText="Importing…" onClick={run}>
            Import file
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-1">
        <label htmlFor="productImportFile" className="text-label font-semibold">
          CSV file
        </label>
        <input
          id="productImportFile"
          name="csv-file"
          type="file"
          accept=".csv,text/csv"
          onChange={choose}
          disabled={busy}
          aria-describedby="productImportHelp"
          className="block w-full rounded-control border border-field-line bg-surface text-body file:mr-3 file:min-h-10 file:cursor-pointer file:border-0 file:border-r file:border-line file:bg-sunken file:px-4 file:font-semibold file:text-ink hover:file:bg-sand"
        />
        <p id="productImportHelp" className="text-small text-ink-muted">
          At most 1&nbsp;MB, and at most 500 lines after the first line.
        </p>
      </div>
      <div className="rounded-control bg-sunken px-3.5 py-3 text-small">
        <p>The first line of the file must be exactly:</p>
        <p className="mt-1 break-all font-mono text-[13px]">{IMPORT_HEADER}</p>
        <Button
          small
          level="quiet"
          className="-ml-2 mt-2"
          onClick={() => downloadCSV("products-example.csv", EXAMPLE_FILE)}
          icon={<Download aria-hidden="true" className="size-4" />}
        >
          Download an example file
        </Button>
      </div>
      <MessagePlace message={error ? { kind: "error", text: error } : null} />
      {result && (
        <div aria-live="polite" className="space-y-3">
          <InlineMessage kind={result.data.rejected.length ? "information" : "success"}>{result.message}</InlineMessage>
          {result.data.rejected.length > 0 && (
            <div role="region" aria-label="Rejected lines table" className="lms-scroll-x max-h-[260px] overflow-y-auto overscroll-contain rounded-control border border-line">
              <table className="w-full border-separate border-spacing-0 text-left">
                <thead className="sticky top-0">
                  <tr>
                    <th scope="col" className="w-20 border-b border-line bg-sunken px-3 py-2 text-right text-small font-semibold text-ink-muted">
                      Line
                    </th>
                    <th scope="col" className="border-b border-line bg-sunken px-3 py-2 text-small font-semibold text-ink-muted">
                      Reason
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.rejected.map((line) => (
                    <tr key={line.line}>
                      <td className="border-b border-line px-3 py-2 text-right tabular">{line.line}</td>
                      <td className="border-b border-line px-3 py-2">{line.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Dialog>
  );
}

function DeactivateDialog({ product, onClose, onDone }: { product: Product; onClose: () => void; onDone: (message: string) => void }) {
  const [reason, setReason] = useState("");
  const { data: impact, error, loading, refresh } = useAdminRead<ProductImpact>(productImpactAddress(product.productID));
  const openOrders = impact?.adminPreviewProductImpactOpenOrderCount ?? 0;
  return (
    <ConfirmDialog
      title={`Deactivate ${product.SKU}?`}
      confirmLabel="Deactivate product"
      busyLabel="Deactivating"
      level="danger-filled"
      canConfirm={reasonReady(reason) && Boolean(impact)}
      onClose={onClose}
      onConfirm={() => setProductListing(product.productID, "INACTIVE", reason)}
      onSuccess={(r) => onDone(r.message)}
    >
      <p>Clients can no longer order this product. Nothing is deleted and no stock number changes.</p>
      {loading && <p role="status" className="text-small text-ink-muted">Checking open orders…</p>}
      {error && <><InlineMessage kind="error">{error}</InlineMessage><Button small onClick={refresh}>Retry</Button></>}
      {impact && (
        <p className="rounded-control bg-sunken px-3.5 py-2.5 text-small">
          {plural(openOrders, "open order contains", "open orders contain")} this product. {openOrders === 1 ? "It continues" : "They continue"} normally.
        </p>
      )}
      <ReasonField value={reason} onChange={setReason} />
    </ConfirmDialog>
  );
}
