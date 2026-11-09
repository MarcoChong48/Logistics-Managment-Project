import React from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Badge, Empty } from '../../shared/ui/primitives.jsx';
import { prettyTime } from '../../shared/format.js';


export default function ReturnsPage({ workspace, renderTask }) {
  const tasks = workspace.tasks || [];
  const returns = workspace.returns || [];

  return <><div className="notice notice-blue"><Icon name="return" /><div><strong>A handoff report is not a receipt.</strong><p>Warehouse confirms the actual return before stock changes or a redelivery request can proceed. A pre-delivery return does not create a failed customer attempt.</p></div></div><div className="section-heading section-spaced"><h2>Packages you can return</h2></div><div className="delivery-grid">{tasks.filter(task => task.canReturn).map(renderTask)}</div>{!tasks.some(task => task.canReturn) && <div className="panel"><Empty icon="return" title="No return action needed.">Packages eligible for a return handoff appear here.</Empty></div>}<section className="panel section-spaced"><div className="panel-heading"><h2>Handoff records</h2><span className="muted small-text">{returns.length} records</span></div>{returns.length ? <div className="return-records">{returns.map(item => <article key={item.id}><div className="return-record-icon"><Icon name="return" size={19} /></div><div><strong className="mono">{item.orderReference}</strong><p>{item.reason}</p><small>{item.packageId} · {prettyTime(item.createdAt)}</small></div><Badge value={item.status} /></article>)}</div> : <Empty icon="history" title="No handoffs recorded yet." />}</section></>;
}
