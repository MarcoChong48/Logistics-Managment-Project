import React, { useState } from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Badge, Empty } from '../../shared/ui/primitives.jsx';
import { label, prettyTime, relativeTime } from '../../shared/format.js';
import ReviewQueue from './ReviewQueue.jsx';


export function useNotificationFilters() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  return { unreadOnly, setUnreadOnly };
}

export default function NotificationsPage({ workspace, filters, navigate }) {
  const { unreadOnly, setUnreadOnly } = filters;
  const notifications = workspace.notifications || [];

  return <div className="notification-layout"><section className="panel"><div className="panel-heading"><h2>Event notifications</h2><label className="checkbox-label"><input type="checkbox" checked={unreadOnly} onChange={event => setUnreadOnly(event.target.checked)} />Unread only</label></div><div className="notification-list">{notifications.filter(item => !unreadOnly || !item.read).map(item => <article key={item.id} className={item.read ? '' : 'unread'}><div className={`notification-symbol ${item.type?.includes('FAIL') ? 'warning' : ''}`}><Icon name={item.type?.includes('FAIL') ? 'alert' : 'bell'} size={19} /></div><div><div className="notification-title"><strong>{label(item.type)}</strong><time>{relativeTime(item.createdAt)}</time></div><p>{item.message}</p><small>{prettyTime(item.createdAt)} · {item.read ? 'Read' : 'Unread'}</small></div></article>)}{!notifications.filter(item => !unreadOnly || !item.read).length && <Empty icon="bell" title="You’re all caught up.">No matching event notifications.</Empty>}</div><p className="panel-footnote">Place holder · Shared notification transport and read-state updates belong to the team notification integration. This view shows saved local events.</p></section><section className="panel"><div className="panel-heading"><h2>Review queue</h2><Badge tone="amber">Manual review</Badge></div><ReviewQueue workspace={workspace} onNavigate={navigate} /></section></div>;
}
