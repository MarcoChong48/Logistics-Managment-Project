import { useEffect, useState } from "react";

/** The printable label of a product: a real QR code that holds exactly the SKU, the SKU and the name. */
export function QRLabel({ sku, name }: { sku: string; name: string }) {
  const [svg, setSvg] = useState("");
  useEffect(() => {
    let alive = true;
    // The QR library is loaded only when a label is opened.
    import("qrcode")
      .then((QRCode) => QRCode.toString(sku, { type: "svg", margin: 0, errorCorrectionLevel: "M", color: { dark: "#24201c", light: "#ffffff" } }))
      .then((text) => {
        if (alive) setSvg(text);
      });
    return () => {
      alive = false;
    };
  }, [sku]);
  return (
    <div className="lms-print-area">
      <div className="mx-auto flex w-full max-w-[300px] flex-col items-center gap-3 rounded-card border-2 border-ink px-6 py-6 text-center">
        <div role="img" aria-label={`QR code that holds ${sku}`} className="size-44 [&>svg]:size-full" dangerouslySetInnerHTML={{ __html: svg }} />
        <p className="text-[20px] font-bold leading-7 tabular">{sku}</p>
        <p className="text-body">{name}</p>
      </div>
    </div>
  );
}
