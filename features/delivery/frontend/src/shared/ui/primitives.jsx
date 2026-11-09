import React, { useEffect, useRef } from 'react';
import Icon from './Icon.jsx';
import { label } from '../format.js';

export function Badge({ value, children, tone }) {
  const success = ['DELIVERED', 'COLLECTED', 'RECEIVED', 'RETURNED', 'INTACT', 'SUCCESS'];
  const danger = ['FAILED', 'DELIVERY_FAILED', 'DAMAGED', 'CANCELLED', 'CANNOT_COLLECT'];
  const warning = ['AWAITING_WAREHOUSE', 'PENDING', 'READY_FOR_REDELIVERY', 'STALE'];
  const color = tone || (success.includes(value) ? 'green' : danger.includes(value) ? 'red' : warning.includes(value) ? 'amber' : 'blue');
  return <span className={`badge badge-${color}`}><span />{children || label(value)}</span>;
}

export function Empty({ icon = 'box', title, children, action }) {
  return <div className="empty"><div className="empty-icon"><Icon name={icon} size={26} /></div><h3>{title}</h3>{children && <p>{children}</p>}{action}</div>;
}

export function Modal({ title, subtitle, children, onClose, wide = false }) {
  const closeRef = useRef(null);
  const dialogRef = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();
    const onKey = event => {
      if (event.key === 'Escape') onClose();
      if (event.key === 'Tab') {
        const nodes = [...dialogRef.current.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]')];
        if (!nodes.length) return;
        if (event.shiftKey && document.activeElement === nodes[0]) { event.preventDefault(); nodes.at(-1).focus(); }
        else if (!event.shiftKey && document.activeElement === nodes.at(-1)) { event.preventDefault(); nodes[0].focus(); }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = previousOverflow; previous?.focus?.(); };
  }, []);
  return <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}><section ref={dialogRef} className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}><header className="modal-head"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button ref={closeRef} className="icon-button" aria-label="Close dialog" onClick={onClose}><Icon name="close" /></button></header>{children}</section></div>;
}
