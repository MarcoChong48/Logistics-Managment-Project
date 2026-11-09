import React, { useEffect, useRef, useState } from 'react';
import { Modal } from '../../shared/ui/primitives.jsx';

export default function CameraScanner({ onScan, onClose }) {
  const video = useRef(null);
  const scanRef = useRef(onScan);
  const [message, setMessage] = useState('Starting your camera…');
  useEffect(() => { scanRef.current = onScan; }, [onScan]);
  useEffect(() => {
    let closed = false, controls = null, handled = false;
    const start = async () => {
      try {
        const { BrowserQRCodeReader } = await import('@zxing/browser');
        const reader = new BrowserQRCodeReader();
        controls = await reader.decodeFromConstraints({ audio: false, video: { facingMode: { ideal: 'environment' } } }, video.current, (result, error, liveControls) => {
          if (result && !handled && !closed) { handled = true; liveControls.stop(); scanRef.current(result.getText()); }
        });
        if (closed) controls.stop();
        else setMessage('Place the pickup-list or package QR code inside the frame.');
      } catch (error) {
        if (!closed) setMessage('Camera unavailable. Allow camera access on localhost or HTTPS, or close this window and enter the code.');
      }
    };
    start();
    return () => { closed = true; controls?.stop(); if (video.current?.srcObject) video.current.srcObject.getTracks().forEach(track => track.stop()); };
  }, []);
  return <Modal title="Scan a QR code" subtitle="Your camera stays on this device." onClose={onClose}><div className="camera-preview"><video ref={video} muted playsInline /><div className="scan-frame" /></div><p className="muted camera-message" role="status">{message}</p><button className="button button-secondary full" onClick={onClose}>Use code entry instead</button></Modal>;
}
