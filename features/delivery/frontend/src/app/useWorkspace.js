import { useCallback, useEffect, useRef, useState } from 'react';

export default function useWorkspace() {
  const [session, setSession] = useState(null);
  const sessionRef = useRef(null);
  const [workspace, setWorkspace] = useState(null);
  const [booting, setBooting] = useState(true);
  const [bootError, setBootError] = useState('');
  const [busy, setBusy] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [notice, setNotice] = useState(null);
  const [lastLoaded, setLastLoaded] = useState(null);
  const requestKeys = useRef(new Map());
  const noticeId = useRef(0);
  const opLock = useRef(false);

  const flash = useCallback((message, tone = 'success', code = '', title = '') => {
    const id = ++noticeId.current;
    setNotice({ message, tone, code, id, title });
    if (tone === 'success') window.setTimeout(() => setNotice(current => current?.id === id ? null : current), 6500);
  }, []);

  const api = useCallback(async (path, options = {}) => {
    const headers = new Headers(options.headers || {});
    if (options.method && options.method !== 'GET') {
      const activeSession = sessionRef.current;
      if (activeSession?.csrfToken) headers.set(activeSession.csrfHeader || 'X-CSRF-TOKEN', activeSession.csrfToken);
    }
    let response;
    try { response = await fetch(path, { credentials: 'same-origin', ...options, headers }); }
    catch { const error = new Error('Cannot reach the Java backend. Your action is not confirmed. Refresh or retry when the connection returns.'); error.code = 'CONNECTION_UNAVAILABLE'; error.uncertain = true; throw error; }
    const contentType = response.headers.get('content-type') || '';
    const data = contentType.includes('application/json') ? await response.json() : null;
    if (!response.ok) {
      if (response.status === 401 && path !== '/api/login') { setWorkspace(null); setSession(current => ({ ...current, authenticated: false })); }
      const error = new Error(data?.message || `The request could not be completed (${response.status}).`);
      error.code = data?.code || `HTTP_${response.status}`; error.status = response.status; error.uncertain = response.status >= 500; throw error;
    }
    return data;
  }, []);

  const loadWorkspace = useCallback(async () => {
    const data = await api('/api/delivery/workspace');
    setWorkspace(data); setLastLoaded(new Date()); return data;
  }, [api]);

  const loadSession = useCallback(async () => {
    const data = await api('/api/session'); sessionRef.current = data; setSession(data);
    if (data.authenticated) await loadWorkspace();
    else setWorkspace(null);
    return data;
  }, [api, loadWorkspace]);

  useEffect(() => { loadSession().catch(error => setBootError(error.message)).finally(() => setBooting(false)); }, [loadSession]);
  useEffect(() => {
    if (!session?.authenticated) return;
    const refreshOnFocus = () => { if (!opLock.current) loadWorkspace().catch(() => {}); };
    window.addEventListener('focus', refreshOnFocus);
    return () => window.removeEventListener('focus', refreshOnFocus);
  }, [session?.authenticated, loadWorkspace]);

  const refresh = async () => { setRefreshing(true); try { await loadWorkspace(); } catch (error) { flash(error.message, 'error', error.code); } finally { setRefreshing(false); } };

  // Keep an uncertain action's request ID so a retry cannot repeat its effect.
  const mutate = async (path, payload = {}, title = 'Saving') => {
    if (opLock.current) return false;
    opLock.current = true; setBusy(title);
    const key = `${path}:${JSON.stringify(payload)}`;
    const requestId = requestKeys.current.get(key) || crypto.randomUUID();
    requestKeys.current.set(key, requestId);
    try {
      const result = await api(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...payload, requestId }) });
      requestKeys.current.delete(key);
      await loadWorkspace(); flash(result?.message || 'Saved successfully.'); return true;
    } catch (error) {
      if (!error.uncertain) requestKeys.current.delete(key);
      flash(error.message, 'error', error.code);
      if (error.status === 409) await loadWorkspace().catch(() => {});
      return false;
    } finally { setBusy(''); opLock.current = false; }
  };

  async function sendMultipart(path, body, key, title, successMessage) {
    if (opLock.current) return false;
    opLock.current = true;
    setBusy(title);
    const requestId = requestKeys.current.get(key) || crypto.randomUUID();
    requestKeys.current.set(key, requestId);
    body.set('requestId', requestId);
    try {
      await api(path, { method: 'POST', body });
      requestKeys.current.delete(key);
      await loadWorkspace();
      flash(successMessage);
      return true;
    } catch (error) {
      if (!error.uncertain) requestKeys.current.delete(key);
      flash(error.message, 'error', error.code);
      return false;
    } finally {
      setBusy('');
      opLock.current = false;
    }
  }

  function retryConnection() {
    setBootError('');
    setBooting(true);
    loadSession().catch(error => setBootError(error.message)).finally(() => setBooting(false));
  }

  async function signOut(resetView) {
    try {
      await api('/api/logout', { method: 'POST' });
      setWorkspace(null);
      resetView();
      await loadSession();
    } catch (error) {
      flash(error.message, 'error', error.code);
    }
  }

  return {
    session, workspace, booting, bootError, busy, refreshing, notice, lastLoaded,
    api, loadSession, retryConnection, refresh, mutate, sendMultipart, flash, signOut,
    dismissNotice: () => setNotice(null),
  };
}
