// Form fields: label above, the input, a help line and an error line linked with aria-describedby.

import { useId, useState, type InputHTMLAttributes, type ReactNode, type Ref, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { AlertTriangle, ChevronDown, Eye, EyeOff } from "lucide-react";
import { cn } from "../lib/cn";

export const inputClass =
  "w-full min-h-10 rounded-control border border-field-line bg-surface px-3 py-2 text-body text-ink max-md:text-[16px] pointer-coarse:min-h-11 " +
  "hover:border-ink-muted disabled:cursor-not-allowed disabled:bg-sunken disabled:text-ink-muted aria-[invalid=true]:border-danger aria-[invalid=true]:border-2";

interface FieldFrameProps {
  label: ReactNode;
  optional?: boolean;
  help?: ReactNode;
  error?: string | null;
  id: string;
  children: ReactNode;
  className?: string;
}

function FieldFrame({ label, optional, help, error, id, children, className }: FieldFrameProps) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-1", className)}>
      <label htmlFor={id} className="text-label font-semibold text-ink">
        {label}
        {optional && <span className="font-normal text-ink-muted"> (optional)</span>}
      </label>
      {children}
      {help && (
        <p id={`${id}-help`} className="text-small text-ink-muted">
          {help}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="flex items-start gap-1.5 text-small font-semibold text-danger">
          <AlertTriangle aria-hidden="true" className="mt-px size-4 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}

/** A field's name for the browser (autofill, password managers): its label in lower case with hyphens. */
function nameFrom(label: ReactNode): string | undefined {
  return typeof label === "string" ? label.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") : undefined;
}

function describedBy(id: string, help: unknown, error: unknown): string | undefined {
  const ids = [help ? `${id}-help` : null, error ? `${id}-error` : null].filter(Boolean);
  return ids.length ? ids.join(" ") : undefined;
}

type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> & {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  help?: ReactNode;
  error?: string | null;
  optional?: boolean;
  fieldClassName?: string;
  inputRef?: Ref<HTMLInputElement>;
};

export function TextField({ label, value, onChange, help, error, optional, fieldClassName, id, className, inputRef, ...rest }: TextFieldProps) {
  const auto = useId();
  const fieldID = id ?? auto;
  return (
    <FieldFrame label={label} optional={optional} help={help} error={error} id={fieldID} className={fieldClassName}>
      <input
        ref={inputRef}
        id={fieldID}
        name={nameFrom(label)}
        autoComplete="off"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(fieldID, help, error)}
        className={cn(inputClass, className)}
        {...rest}
      />
    </FieldFrame>
  );
}

type TextAreaFieldProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "onChange" | "value"> & {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  help?: ReactNode;
  error?: string | null;
  optional?: boolean;
};

export function TextAreaField({ label, value, onChange, help, error, optional, id, rows = 3, ...rest }: TextAreaFieldProps) {
  const auto = useId();
  const fieldID = id ?? auto;
  return (
    <FieldFrame label={label} optional={optional} help={help} error={error} id={fieldID}>
      <textarea
        id={fieldID}
        name={nameFrom(label)}
        autoComplete="off"
        rows={rows}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(fieldID, help, error)}
        className={cn(inputClass, "min-h-[88px] resize-y leading-[22px]")}
        {...rest}
      />
    </FieldFrame>
  );
}

export interface Option {
  value: string;
  label: string;
}

type SelectFieldProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, "onChange" | "value"> & {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  options: Option[];
  help?: ReactNode;
  error?: string | null;
  fieldClassName?: string;
};

export function SelectField({ label, value, onChange, options, help, error, id, fieldClassName, ...rest }: SelectFieldProps) {
  const auto = useId();
  const fieldID = id ?? auto;
  return (
    <FieldFrame label={label} help={help} error={error} id={fieldID} className={fieldClassName}>
      <SelectBox id={fieldID} name={nameFrom(label)} value={value} onChange={onChange} options={options} aria-invalid={error ? true : undefined} aria-describedby={describedBy(fieldID, help, error)} {...rest} />
    </FieldFrame>
  );
}

/** A select without its own label (for filter bars, which name it with aria-label). */
export function SelectBox({ value, onChange, options, className, ...rest }: Omit<SelectHTMLAttributes<HTMLSelectElement>, "onChange" | "value"> & { value: string; onChange: (value: string) => void; options: Option[] }) {
  return (
    <span className={cn("relative block min-w-0", className)}>
      <select value={value} onChange={(e) => onChange(e.target.value)} className={cn(inputClass, "appearance-none pr-9")} {...rest}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <ChevronDown aria-hidden="true" className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
    </span>
  );
}

export function CheckboxField({ label, checked, onChange, help, id, className }: { label: ReactNode; checked: boolean; onChange: (checked: boolean) => void; help?: ReactNode; id?: string; className?: string }) {
  const auto = useId();
  const fieldID = id ?? auto;
  return (
    <div className={cn("flex flex-col", className)}>
      <label htmlFor={fieldID} className="flex min-h-10 cursor-pointer items-start gap-3 py-2 text-body pointer-coarse:min-h-11">
        <input
          id={fieldID}
          name={nameFrom(label)}
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          aria-describedby={help ? `${fieldID}-help` : undefined}
          className="mt-0.5 size-5 shrink-0 cursor-pointer rounded accent-teal"
        />
        <span>{label}</span>
      </label>
      {help && (
        <p id={`${fieldID}-help`} className="-mt-1 pl-8 text-small text-ink-muted">
          {help}
        </p>
      )}
    </div>
  );
}

/** The field "Reason" of every window that ends or changes something: at least 5 characters. */
export function ReasonField({
  value,
  onChange,
  error,
  label = "Reason",
  help = "At least 5 characters.",
  single = false,
}: {
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  label?: string;
  help?: string;
  single?: boolean;
}) {
  return single ? (
    <TextField label={label} value={value} onChange={onChange} help={help} error={error} autoComplete="off" />
  ) : (
    <TextAreaField label={label} value={value} onChange={onChange} help={help} error={error} />
  );
}

export const reasonReady = (text: string) => text.trim().length >= 5;

/** A password input with a worded Show / Hide button. */
export function PasswordInput({
  id,
  name,
  value,
  onChange,
  label,
  autoComplete,
  describedBy: described,
  invalid,
  inputRef,
}: {
  id: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  label: string;
  autoComplete: string;
  describedBy?: string;
  invalid?: boolean;
  inputRef?: (element: HTMLInputElement | null) => void;
}) {
  const [shown, setShown] = useState(false);
  return (
    <div className="flex gap-2">
      <input
        ref={inputRef}
        id={id}
        name={name}
        type={shown ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        aria-describedby={described}
        aria-invalid={invalid || undefined}
        spellCheck={false}
        autoCapitalize="none"
        className={cn(inputClass, "min-w-0 flex-1 font-mono tracking-[0.02em]")}
      />
      <button
        type="button"
        onClick={() => setShown((s) => !s)}
        aria-label={`${shown ? "Hide" : "Show"} ${label.toLowerCase()}`}
        aria-pressed={shown}
        className="inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-control border border-field-line bg-surface px-3 text-label font-semibold hover:bg-hover pointer-coarse:min-h-11"
      >
        {shown ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
        {shown ? "Hide" : "Show"}
      </button>
    </div>
  );
}
