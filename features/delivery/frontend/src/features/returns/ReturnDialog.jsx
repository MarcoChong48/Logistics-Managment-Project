import React, { useState } from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Modal } from '../../shared/ui/primitives.jsx';

export default function ReturnDialog({ task, busy, onClose, mutate }) {
  const onSubmit = async reason => {
    const path = `/api/packages/${encodeURIComponent(task.id)}/return`;
    if (await mutate(path, { reason }, 'Reporting return handoff')) onClose();
  };
  const [reason, setReason] = useState(task.status === 'DELIVERY_FAILED' ? 'Returning package after failed delivery' : '');
  return <Modal title="Report return handoff" subtitle={`${task.orderReference} · ${task.id}`} onClose={onClose}><form onSubmit={event => { event.preventDefault(); onSubmit(reason.trim()); }}><div className="notice notice-blue"><Icon name="info" /><p>You keep recorded custody until Warehouse confirms receipt. This report does not restore stock or authorize another delivery.</p></div><label className="field"><span>Return reason <em>Required</em></span><textarea value={reason} onChange={event => setReason(event.target.value)} rows="3" maxLength="600" required placeholder="For example, a vehicle problem before starting delivery." /></label><div className="modal-footer"><button className="button button-secondary" type="button" disabled={busy} onClick={onClose}>Cancel</button><button className="button button-primary" type="submit" disabled={busy || !reason.trim()}>Report handoff</button></div></form></Modal>;
}
