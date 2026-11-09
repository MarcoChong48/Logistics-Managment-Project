import React, { useEffect, useState } from 'react';
import { Modal } from '../../shared/ui/primitives.jsx';
import { label } from '../../shared/format.js';

function PrivateProof({ proof }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true, objectUrl = '';
    const load = async () => {
      try {
        const response = await fetch(`/api/proofs/${encodeURIComponent(proof.id)}`, { credentials: 'same-origin', cache: 'no-store' });
        if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.message || 'This proof is no longer available to this worker.'); }
        const blob = await response.blob(); objectUrl = URL.createObjectURL(blob); if (active) setUrl(objectUrl);
      } catch (error) { if (active) setError(error.message); }
    };
    load(); return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [proof.id]);
  return error ? <div className="form-error" role="alert">{error}</div> : url ? <img className="private-proof-image" src={url} alt={`${label(proof.type)} delivery proof`} /> : <p className="muted">Loading private proof…</p>;
}

export default function ProofPreview({ proof: proofPreview, onClose }) {
  return <Modal title="Delivery proof" subtitle="Private access is checked by the backend." onClose={onClose}><PrivateProof proof={proofPreview} /><p className="panel-footnote">Access ends after completion or confirmed return. Do not copy recipient proof into shared logs.</p></Modal>;
}
