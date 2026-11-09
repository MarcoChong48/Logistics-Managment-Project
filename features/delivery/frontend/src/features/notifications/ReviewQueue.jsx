import React from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Empty } from '../../shared/ui/primitives.jsx';
import { label } from '../../shared/format.js';

export default function ReviewQueue({ workspace, compact = false, onNavigate }) {
  // Decision U-10 and U-12, B: Backend review ages start at actual receipt, not a handoff report.
  const items = (workspace.reviewQueue || []).map(item => {
    const type = String(item.type || '');
    return { id: item.id, title: item.title || label(item.type), body: `${item.orderReference || item.reference || ''}${item.orderReference || item.reference ? ' · ' : ''}${item.message}`, page: type.includes('CLAIM') ? 'pickup' : type.includes('FAIL') ? 'history' : 'returns', icon: type.includes('CLAIM') ? 'scan' : type.includes('FAIL') ? 'alert' : type.includes('RETURN') ? 'return' : 'clock' };
  });
  const visible = compact ? items.slice(0, 3) : items;
  return <div className="review-queue">{visible.map(item => <button key={item.id} onClick={() => onNavigate(item.page)}><span className="review-icon"><Icon name={item.icon} size={18} /></span><span><strong>{item.title}</strong><small>{item.body}</small></span><Icon name="chevron" size={16} /></button>)}{!items.length && <Empty icon="check" title="Nothing needs review.">Stale claims, repeated failures and pending returns appear here.</Empty>}{compact && items.length > 3 && <button className="text-button" onClick={() => onNavigate('notifications')}>View {items.length - 3} more review items<Icon name="arrow" size={15} /></button>}<p className="review-footnote">Place holder · Client reminders and Admin case review need the team integrations. No automatic cancellation or redelivery.</p></div>;
}
