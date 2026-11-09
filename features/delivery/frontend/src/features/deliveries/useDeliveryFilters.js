import { useEffect, useState } from 'react';
import { label } from '../../shared/format.js';

export default function useDeliveryFilters(userId, flash) {
  const [filters, setFilters] = useState({ search: '', status: 'ALL' });
  const [savedFilters, setSavedFilters] = useState([]);
  // Decision U-14, B: Save display preferences locally, never business results or contact data.
  useEffect(() => {
    if (!userId) return;
    try { const stored = JSON.parse(localStorage.getItem(`nexus-filters-${userId}`) || '[]'); setSavedFilters(Array.isArray(stored) ? stored.filter(item => typeof item.name === 'string' && item.search !== undefined && item.status).slice(0, 8) : []); }
    catch { setSavedFilters([]); }
  }, [userId]);
  const saveFilter = () => {
    const name = `${filters.status === 'ALL' ? 'All statuses' : label(filters.status)}${filters.search ? ` · ${filters.search}` : ''}`;
    const next = [{ ...filters, name }, ...savedFilters.filter(item => item.name !== name)].slice(0, 8);
    setSavedFilters(next); try { localStorage.setItem(`nexus-filters-${userId}`, JSON.stringify(next)); flash('Filter saved on this device.', 'success', '', 'Preference saved'); } catch { flash('This browser could not save your filter preference.', 'error'); }
  };
  const clearSavedFilters = () => {
    setSavedFilters([]);
    try { localStorage.removeItem(`nexus-filters-${userId}`); } catch {}
  };

  return { filters, setFilters, savedFilters, saveFilter, clearSavedFilters };
}
