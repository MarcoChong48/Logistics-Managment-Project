export const FAILURE_REASONS = [['CLIENT_NOT_HOME', 'Client not home'], ['WRONG_ADDRESS', 'Wrong address'], ['CLIENT_REFUSED', 'Client refused'], ['UNABLE_TO_CONTACT', 'Unable to contact client'], ['OTHER', 'Other']];
const STATUS_LABELS = { READY_FOR_PICKUP: 'Ready for pickup', READY_FOR_REDELIVERY: 'Ready for redelivery', OUT_FOR_DELIVERY: 'Out for delivery', SHIPPED: 'Collected', DELIVERED: 'Delivered', DELIVERY_FAILED: 'Delivery failed', AWAITING_WAREHOUSE: 'Awaiting Warehouse', IN_PROGRESS: 'In progress', FAILED: 'Failed', OPEN: 'Open', CLOSED: 'Closed', CANCELLED: 'Cancelled', INTACT: 'Intact', DAMAGED: 'Damaged', PENDING: 'Pending', COLLECTED: 'Collected', CANNOT_COLLECT: 'Cannot collect', RECEIVED: 'Received', RETURNED: 'Returned' };
export const label = value => STATUS_LABELS[value] || String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase());
export const prettyTime = value => value ? new Date(value).toLocaleString('en-HK', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Hong_Kong' }) : '—';
export const relativeTime = value => {
  if (!value) return '';
  const minutes = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 60000));
  return minutes < 1 ? 'Just now' : minutes < 60 ? `${minutes}m ago` : minutes < 1440 ? `${Math.floor(minutes / 60)}h ago` : `${Math.floor(minutes / 1440)}d ago`;
};
