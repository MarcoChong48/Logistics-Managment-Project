import React from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Empty } from '../../shared/ui/primitives.jsx';
import { label } from '../../shared/format.js';
import OutcomePanel from './OutcomePanel.jsx';


export default function DeliveriesPage({
  workspace, busy, selectedTask, deliveryFilters, renderTask, onPreview, requestConfirmation, sendMultipart, flash
}) {
  const { filters, setFilters, savedFilters, saveFilter, clearSavedFilters } = deliveryFilters;
  const tasks = workspace.tasks || [];
  const attempts = workspace.attempts || [];
  const selected = tasks.find(task => task.id === selectedTask);
  const visibleTasks = tasks.filter(task => (filters.status === 'ALL' || task.status === filters.status) && `${task.id} ${task.orderReference} ${task.itemSummary || ''} ${task.recipientName || ''}`.toLowerCase().includes(filters.search.toLowerCase()));

  async function uploadProof(task, type, file, onSuccess) {
    if (!file) return;
    const limit = workspace.settings?.photoLimitBytes || 5242880;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      flash('Choose a JPEG or PNG image.', 'error', 'UNSUPPORTED_IMAGE');
      return;
    }
    if (file.size > limit) {
      flash(`This image is too large. The current local limit is ${Math.round(limit / 1024 / 1024)} MB.`, 'error', 'IMAGE_TOO_LARGE');
      return;
    }

    const key = `proof:${task.id}:${type}:${file.name}:${file.size}:${file.lastModified}`;
    const body = new FormData();
    body.set('type', type);
    body.set('file', file);
    const saved = await sendMultipart(
      `/api/packages/${encodeURIComponent(task.id)}/proofs`,
      body,
      key,
      'Saving proof',
      `${type === 'PHOTO' ? 'Photo' : 'Signature'} proof saved privately. You can now confirm the outcome.`,
    );
    if (saved) onSuccess?.();
  }

  return <><div className="filter-bar"><label className="search-field"><Icon name="search" size={18} /><input placeholder="Search order, package or recipient" aria-label="Search deliveries" value={filters.search} onChange={event => setFilters({ ...filters, search: event.target.value })} /></label><label className="select-field"><span className="sr-only">Delivery status</span><select value={filters.status} onChange={event => setFilters({ ...filters, status: event.target.value })}><option value="ALL">All statuses</option>{['SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED'].map(status => <option value={status} key={status}>{label(status)}</option>)}</select></label><button className="button button-secondary" onClick={saveFilter}><Icon name="save" size={16} />Save filter</button></div>{savedFilters.length > 0 && <div className="saved-filters"><span>Saved on this device</span>{savedFilters.map(item => <button key={item.name} onClick={() => setFilters({ search: item.search, status: item.status })}>{item.name}<Icon name="chevron" size={12} /></button>)}<button className="text-button" onClick={clearSavedFilters}>Clear saved</button></div>}<div className="delivery-grid">{visibleTasks.map(renderTask)}</div>{!visibleTasks.length && <div className="panel"><Empty icon="truck" title={tasks.length ? 'No deliveries match this filter.' : 'No collected packages yet.'}>{tasks.length ? 'Try another status or search term.' : 'Collect a package from your pickup list to begin.'}</Empty></div>}{selected && <OutcomePanel key={`${selected.id}:${selected.activeAttemptId}`} task={selected} attempts={attempts} busy={!!busy} onUpload={(type, file, clear) => uploadProof(selected, type, file, clear)} onPreview={onPreview} onSubmit={payload => requestConfirmation(payload.outcome === 'DELIVERED' ? 'Confirm successful delivery?' : 'Record failed attempt?', payload.outcome === 'DELIVERED' ? `Save the selected proof and mark ${selected.orderReference} delivered. This completes the attempt and removes your access to its contact details and proof files.` : `Record the failed customer attempt. You keep custody until Warehouse confirms the returned package. No automatic redelivery is approved.`, `/api/packages/${encodeURIComponent(selected.id)}/outcome`, payload, { buttonText: payload.outcome === 'DELIVERED' ? 'Confirm delivered' : 'Record failed attempt', danger: payload.outcome === 'FAILED' })} />}</>;
}
