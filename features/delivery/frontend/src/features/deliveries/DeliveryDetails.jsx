import React from 'react';
import Icon from '../../shared/ui/Icon.jsx';
import { Modal } from '../../shared/ui/primitives.jsx';
import { label } from '../../shared/format.js';


export default function DeliveryDetails({ task: detailTask, onClose }) {
  return <Modal title={detailTask.orderReference} subtitle={`${detailTask.id} · ${label(detailTask.status)}`} onClose={onClose}><div className="detail-section"><span className="eyebrow">PACKAGE CONTENTS</span><div className="detail-products">{detailTask.products?.map(item => <div key={item.sku}><span>{item.name}<small className="mono">{item.sku}</small></span><strong>× {item.quantity}</strong></div>)}</div></div>{detailTask.recipientName ? <div className="detail-section"><span className="eyebrow">AUTHORIZED DELIVERY CONTACT</span><dl className="contact-details"><div><dt>Recipient</dt><dd>{detailTask.recipientName}</dd></div><div><dt>Phone</dt><dd><a href={`tel:${detailTask.phone?.replace(/[^+0-9]/g, '')}`}>{detailTask.phone}</a></dd></div><div><dt>Address</dt><dd>{detailTask.address}</dd></div></dl></div> : <div className="notice notice-blue"><Icon name="lock" /><p>Contact access ended when delivery completed or Warehouse received the return.</p></div>}<div className="modal-footer"><button className="button button-secondary" onClick={onClose}>Close</button></div></Modal>;
}
