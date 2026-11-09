import React, { useState } from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Badge, Empty } from '../../shared/ui/primitives.jsx';
import { FAILURE_REASONS, label, prettyTime } from '../../shared/format.js';

function AttemptCard({ attempt, canPreview, onPreview }) {
  return <article className="panel attempt-card"><div className="attempt-heading"><div className={`attempt-symbol ${attempt.outcome === 'FAILED' ? 'failed' : attempt.outcome === 'DELIVERED' ? 'success' : ''}`}><Icon name={attempt.outcome === 'FAILED' ? 'alert' : attempt.outcome === 'DELIVERED' ? 'check' : 'truck'} size={20} /></div><div><h3 className="mono">{attempt.orderReference}<span>{attempt.id}</span></h3><p>{attempt.packageId} · {attempt.workerName}</p></div><Badge value={attempt.outcome} /></div><div className="attempt-meta"><span><Icon name="clock" size={15} />Started {prettyTime(attempt.startedAt)}</span>{attempt.endedAt && <span>Finished {prettyTime(attempt.endedAt)}</span>}</div>{attempt.reason && <div className="attempt-reason"><strong>{FAILURE_REASONS.find(item => item[0] === attempt.reason)?.[1] || label(attempt.reason)}</strong>{attempt.note && <p>{attempt.note}</p>}</div>}{attempt.proofs?.length > 0 && <div className="attempt-proofs">{attempt.proofs.map(proof => <div key={proof.id}><Icon name={proof.type === 'PHOTO' ? 'camera' : 'pen'} size={16} /><span>{label(proof.type)}<small className="mono">{proof.id} · {prettyTime(proof.createdAt)}</small></span>{canPreview && proof.url ? <button className="text-button" onClick={() => onPreview(proof)}>Preview</button> : <span className="proof-locked"><Icon name="lock" size={13} />Metadata only</span>}</div>)}</div>}<div className="history-note"><Icon name="history" size={14} />Read-only attempt record{!canPreview && ' · Private contact and proof content are not exposed here.'}</div></article>;
}

// Decision U-09, B: Keep minimal history after detailed attempts and proof expire.
function RetainedHistory({ summaries }) {
  if (!summaries.length) return null;
  return <section className="panel section-spaced"><div className="panel-heading"><div><h2>Retained order summaries</h2><p className="muted small-text">Detailed attempt records and proof have reached their retention limit.</p></div></div><div className="return-records">{summaries.map((summary, index) => <article key={`${summary.packageId}:${summary.eventAt}:${index}`}><div className="return-record-icon"><Icon name="history" size={18} /></div><div><strong className="mono">{summary.orderReference}</strong><p>{summary.packageId} · {prettyTime(summary.eventAt)}</p><small>Minimal summary only · No private contact, proof or internal notes</small></div><Badge value={summary.outcome} /></article>)}</div></section>;
}

export function useHistoryFilters() {
  const [historySearch, setHistorySearch] = useState('');
  const [historyOutcome, setHistoryOutcome] = useState('ALL');
  return { historySearch, setHistorySearch, historyOutcome, setHistoryOutcome };
}

export default function HistoryPage({ workspace, filters, onPreview }) {
  const { historySearch, setHistorySearch, historyOutcome, setHistoryOutcome } = filters;
  const tasks = workspace.tasks || [];
  const attempts = workspace.attempts || [];
  const history = attempts.filter(attempt => (historyOutcome === 'ALL' || attempt.outcome === historyOutcome) && `${attempt.orderReference} ${attempt.packageId} ${attempt.id}`.toLowerCase().includes(historySearch.toLowerCase())).sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
  const proofAvailable = attempt => tasks.some(task => task.id === attempt.packageId && task.canDeliver && task.activeAttemptId === attempt.id);

  return <><div className="filter-bar"><label className="search-field"><Icon name="search" size={18} /><input aria-label="Search attempt history" placeholder="Search order, package or attempt ID" value={historySearch} onChange={event => setHistorySearch(event.target.value)} /></label><label className="select-field"><span className="sr-only">Attempt outcome</span><select value={historyOutcome} onChange={event => setHistoryOutcome(event.target.value)}><option value="ALL">All outcomes</option><option value="IN_PROGRESS">In progress</option><option value="DELIVERED">Delivered</option><option value="FAILED">Failed</option></select></label></div><div className="history-stack">{history.map(attempt => <AttemptCard key={attempt.id} attempt={attempt} canPreview={proofAvailable(attempt)} onPreview={onPreview} />)}{!history.length && <div className="panel"><Empty icon="history" title="No attempts to show.">Starting a delivery creates a separate attempt record. Pickup issues are recorded separately.</Empty></div>}</div><RetainedHistory summaries={workspace.retainedSummaries || []} /></>;
}
