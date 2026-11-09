import React, { useState } from 'react';
import Icon from '../../shared/ui/Icon.jsx';

export default function Login({ session, api, onLogin }) {
  const [username, setUsername] = useState('ken');
  const [password, setPassword] = useState('Delivery123!');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const login = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try { await api('/api/login', { method: 'POST', body: new URLSearchParams({ username, password }) }); await onLogin(); }
    catch (error) { setError(error.message); }
    finally { setBusy(false); }
  };
  return <main className="login-page" data-testid="login-page"><section className="login-story"><div className="brand"><span className="brand-symbol"><Icon name="box" size={29} /></span>NEXUS<span className="brand-divider" />DELIVERY</div><div className="login-story-main"><span className="eyebrow light">THE NEXT HANDOFF, MADE CLEAR</span><h1>A better day<br />at your doorstep.</h1><p>One workspace for every pickup,<br />delivery and return.</p><div className="login-journey"><span><Icon name="scan" />Collect</span><i /><span><Icon name="truck" />Deliver</span><i /><span><Icon name="check" />Confirm</span></div></div><div className="login-bottom"><span className="status-dot" /> Local Java backend · Durable delivery records</div></section><section className="login-form-panel"><div className="login-card"><span className="eyebrow">DELIVERY WORKSPACE</span><h2>Welcome back.</h2><p className="login-intro">Sign in to pick up where you left off.</p><div className="notice notice-blue"><Icon name="info" /><div><strong>Place holder · Demo authentication</strong><p>Shared team authentication is not connected. Use a local demo worker below. Delivery actions save to the Java backend.</p></div></div><form onSubmit={login}><label className="field"><span>Username</span><input autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} required /></label><label className="field"><span>Password</span><input type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required /></label>{error && <div className="form-error" role="alert"><Icon name="alert" size={17} />{error}</div>}<button className="button button-primary full login-submit" disabled={busy || !session?.csrfToken}>{busy ? 'Signing in…' : 'Sign in to workspace'}<Icon name="arrow" size={18} /></button></form><div className="demo-credentials"><span>Local demo workers</span><code>ken</code><code>mei</code><span>Password: <code>Delivery123!</code></span></div><p className="login-foot">Place holder · Sample people, packages and addresses are for the local integration demo.</p></div></section></main>;
}
