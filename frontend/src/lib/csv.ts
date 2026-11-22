// CSV files. Every "Download CSV" button builds a real file from the rows that its list shows.

function quote(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? "" : String(value);
  // A cell that starts with = + - @ could run as a formula in a spreadsheet; a leading quote stops that.
  const safe = /^[=+\-@]/.test(text) && !/^-?\d/.test(text) ? `'${text}` : text;
  return /[",\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

/** Builds the text of a CSV file from a header and rows. */
export function buildCSV(header: string[], rows: Array<Array<string | number | null | undefined>>): string {
  return [header, ...rows].map((row) => row.map(quote).join(",")).join("\r\n") + "\r\n";
}

/** Starts the download of a CSV file in the browser. */
export function downloadCSV(fileName: string, text: string): void {
  const blob = new Blob(["﻿", text], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** Reads a CSV file into rows of cells (handles quotes and commas inside quotes). */
export function parseCSV(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let inQuotes = false;
  const source = text.replace(/^﻿/, "");
  for (let i = 0; i < source.length; i += 1) {
    const ch = source[i];
    if (inQuotes) {
      if (ch === '"' && source[i + 1] === '"') {
        cell += '"';
        i += 1;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        cell += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(cell);
      cell = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && source[i + 1] === "\n") i += 1;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += ch;
    }
  }
  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}
