import React, { useState } from 'react';
import Login from '../features/access/Login.jsx';
import DeliveryCard from '../features/deliveries/DeliveryCard.jsx';
import DeliveriesPage from '../features/deliveries/DeliveriesPage.jsx';
import DeliveryDetails from '../features/deliveries/DeliveryDetails.jsx';
import ProofPreview from '../features/deliveries/ProofPreview.jsx';
import useDeliveryFilters from '../features/deliveries/useDeliveryFilters.js';
import HistoryPage, { useHistoryFilters } from '../features/history/HistoryPage.jsx';
import IntegrationsPage from '../features/integrations/IntegrationsPage.jsx';
import Sandbox from '../features/integrations/Sandbox.jsx';
import NotificationsPage, { useNotificationFilters } from '../features/notifications/NotificationsPage.jsx';
import ReviewQueue from '../features/notifications/ReviewQueue.jsx';
import OverviewPage from '../features/overview/OverviewPage.jsx';
import PickupPage from '../features/pickup/PickupPage.jsx';
import ReturnDialog from '../features/returns/ReturnDialog.jsx';
import ReturnsPage from '../features/returns/ReturnsPage.jsx';
import Icon from '../shared/ui/Icon.jsx';
import { Modal } from '../shared/ui/primitives.jsx';
import useWorkspace from './useWorkspace.js';
import WorkspaceShell from './WorkspaceShell.jsx';

export default function App() {
  const app = useWorkspace();
  const { workspace, session, busy, mutate, flash } = app;
  const [page, setPage] = useState('overview');
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedTask, setSelectedTask] = useState(null);
  const [details, setDetails] = useState(null);
  const [returnTask, setReturnTask] = useState(null);
  const [proofPreview, setProofPreview] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const deliveryFilters = useDeliveryFilters(session?.user?.id, flash);
  const historyFilters = useHistoryFilters();
  const notificationFilters = useNotificationFilters();

  function navigate(next) {
    setPage(next);
    setMenuOpen(false);
    setSelectedTask(null);
    window.scrollTo({ top: 0, behavior: 'instant' });
  }

  function openOutcome(task) {
    setPage('deliveries');
    setSelectedTask(task.id);
    window.setTimeout(() => {
      document.getElementById('outcome-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 60);
  }

  function requestConfirmation(title, description, path, payload, options = {}) {
    setConfirm({ title, description, path, payload, ...options });
  }

  function resetView() {
    setSelectedTask(null);
    setDetails(null);
    deliveryFilters.setFilters({ search: '', status: 'ALL' });
    setPage('overview');
  }

  if (app.booting || app.bootError) {
    return <ConnectionScreen error={app.bootError} onRetry={app.retryConnection} />;
  }
  if (!session?.authenticated || !workspace) {
    return <Login session={session} api={app.api} onLogin={app.loadSession} />;
  }

  const tasks = workspace.tasks || [];
  const detailTask = tasks.find(task => task.id === details);
  const currentReturnTask = tasks.find(task => task.id === returnTask);
  const renderTask = task => (
    <DeliveryCard
      key={task.id}
      task={task}
      busy={busy}
      onDetails={setDetails}
      onReturn={setReturnTask}
      onOutcome={openOutcome}
      requestConfirmation={requestConfirmation}
    />
  );

  let screen;
  switch (page) {
    case 'pickup':
      screen = <PickupPage workspace={workspace} busy={busy} mutate={mutate} requestConfirmation={requestConfirmation} />;
      break;
    case 'deliveries':
      screen = <DeliveriesPage
        workspace={workspace}
        busy={busy}
        selectedTask={selectedTask}
        deliveryFilters={deliveryFilters}
        renderTask={renderTask}
        onPreview={setProofPreview}
        requestConfirmation={requestConfirmation}
        sendMultipart={app.sendMultipart}
        flash={flash}
      />;
      break;
    case 'returns':
      screen = <ReturnsPage workspace={workspace} renderTask={renderTask} />;
      break;
    case 'history':
      screen = <HistoryPage workspace={workspace} filters={historyFilters} onPreview={setProofPreview} />;
      break;
    case 'notifications':
      screen = <NotificationsPage workspace={workspace} filters={notificationFilters} navigate={navigate} />;
      break;
    case 'integrations':
      screen = <IntegrationsPage workspace={workspace} />;
      break;
    case 'sandbox':
      screen = <Sandbox workspace={workspace} busy={!!busy} onAction={requestConfirmation} />;
      break;
    default:
      screen = <OverviewPage
        workspace={workspace}
        navigate={navigate}
        renderTask={renderTask}
        reviewQueue={<ReviewQueue workspace={workspace} compact onNavigate={navigate} />}
      />;
  }

  const overlays = <>
    {detailTask && <DeliveryDetails task={detailTask} onClose={() => setDetails(null)} />}
    {confirm && <Confirmation
      confirm={confirm}
      busy={busy}
      onClose={() => { if (!busy) setConfirm(null); }}
      onAccept={async () => {
        const saved = await mutate(confirm.path, confirm.payload, confirm.buttonText || 'Saving');
        if (saved) {
          setConfirm(null);
          setSelectedTask(null);
        }
      }}
    />}
    {currentReturnTask && <ReturnDialog
      task={currentReturnTask}
      busy={!!busy}
      onClose={() => { if (!busy) setReturnTask(null); }}
      mutate={mutate}
    />}
    {proofPreview && <ProofPreview proof={proofPreview} onClose={() => setProofPreview(null)} />}
  </>;

  return <WorkspaceShell
    workspace={workspace}
    page={page}
    navigate={navigate}
    menuOpen={menuOpen}
    setMenuOpen={setMenuOpen}
    busy={busy}
    refreshing={app.refreshing}
    refresh={app.refresh}
    lastLoaded={app.lastLoaded}
    notice={app.notice}
    dismissNotice={app.dismissNotice}
    onSignOut={() => app.signOut(resetView)}
    overlays={overlays}
  >
    {screen}
  </WorkspaceShell>;
}

function ConnectionScreen({ error, onRetry }) {
  if (error) {
    return <div className="boot-screen">
      <Icon name="alert" size={36} />
      <h1>Workspace unavailable</h1>
      <p>{error}</p>
      <button className="button button-primary" onClick={onRetry}>Try again</button>
    </div>;
  }
  return <div className="boot-screen">
    <div className="brand-symbol"><Icon name="box" size={32} /></div>
    <h1>NEXUS <span>DELIVERY</span></h1>
    <span className="loading-line" />
    <p>Connecting to your workspace…</p>
  </div>;
}

function Confirmation({ confirm, busy, onClose, onAccept }) {
  return <Modal title={confirm.title} onClose={onClose}>
    <p className="confirmation-description">{confirm.description}</p>
    {confirm.placeholder && <div className="notice notice-amber">
      <Icon name="info" />
      <p><strong>Place holder · Simulated {confirm.actor}.</strong> This records a local demo integration event, not confirmation by another team member.</p>
    </div>}
    <div className="modal-footer">
      <button className="button button-secondary" disabled={!!busy} onClick={onClose}>
        Keep current state
      </button>
      <button className={'button ' + (confirm.danger ? 'button-danger' : 'button-primary')} disabled={!!busy} onClick={onAccept}>
        {busy ? 'Saving…' : confirm.buttonText || 'Confirm action'}
      </button>
    </div>
  </Modal>;
}
