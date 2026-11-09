import React, { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../../shared/ui/Icon.jsx';

// Decision U-09, B: Capture real proof and send it to the private backend endpoint.
export default function SignaturePad({ disabled, onUpload, busy }) {
  const canvasRef = useRef(null);
  const strokes = useRef([]);
  const drawing = useRef(false);
  const [hasInk, setHasInk] = useState(false);
  const redraw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const scale = window.devicePixelRatio || 1;
    canvas.width = Math.round(rect.width * scale);
    canvas.height = Math.round(rect.height * scale);
    const ctx = canvas.getContext('2d');
    ctx.scale(scale, scale);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, rect.width, rect.height);
    ctx.lineWidth = 2.4; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = '#152039';
    for (const stroke of strokes.current) {
      ctx.beginPath();
      stroke.forEach((point, index) => { const x = point.x * rect.width; const y = point.y * rect.height; if (!index) ctx.moveTo(x, y); else ctx.lineTo(x, y); });
      ctx.stroke();
    }
  }, []);
  useEffect(() => {
    const observer = new ResizeObserver(redraw);
    observer.observe(canvasRef.current);
    redraw();
    return () => observer.disconnect();
  }, [redraw]);
  const point = event => { const r = canvasRef.current.getBoundingClientRect(); return { x: Math.min(1, Math.max(0, (event.clientX - r.left) / r.width)), y: Math.min(1, Math.max(0, (event.clientY - r.top) / r.height)) }; };
  const clear = () => { strokes.current = []; setHasInk(false); redraw(); };
  const upload = () => canvasRef.current.toBlob(blob => { if (blob) onUpload(new File([blob], 'recipient-signature.png', { type: 'image/png', lastModified: 0 }), clear); }, 'image/png');
  return <div className="proof-capture"><div className="capture-title"><span><Icon name="pen" size={17} /> Recipient signature</span><button className="text-button" disabled={busy || !hasInk} onClick={clear}>Clear</button></div><div className={`signature-wrap ${disabled ? 'disabled' : ''}`}><canvas ref={canvasRef} aria-label="Draw recipient signature" onPointerDown={event => { if (disabled || busy) return; event.preventDefault(); drawing.current = true; strokes.current.push([point(event)]); event.currentTarget.setPointerCapture(event.pointerId); }} onPointerMove={event => { if (!drawing.current) return; event.preventDefault(); strokes.current.at(-1).push(point(event)); setHasInk(true); redraw(); }} onPointerUp={() => { drawing.current = false; }} onPointerCancel={() => { drawing.current = false; }} />{!hasInk && <span className="signature-hint"><Icon name="pen" size={23} />Sign here with a finger or mouse</span>}</div><button className="button button-secondary small full" disabled={disabled || !hasInk || busy} onClick={upload}><Icon name="upload" size={16} /> Save signature proof</button></div>;
}
