import React, { useEffect, useState } from 'react';

/**
 * The parent brand shown around the products. Deepwater stays as the backup brand; Nibble is the
 * proposed new name, with our own mouse mascot. The choice is per browser and changes only the
 * name, mark and tagline, never the products or the account.
 */
export const BRANDS = {
  deepwater: { id: 'deepwater', name: 'Deepwater', tagline: 'Go deeper.' },
  nibble: { id: 'nibble', name: 'Nibble', tagline: 'A little every day.' },
};

const KEY = 'deepwater-brand';
const EVENT = 'deepwater-brand';

export function readBrand() {
  try { return BRANDS[localStorage.getItem(KEY)] ? localStorage.getItem(KEY) : 'deepwater'; } catch { return 'deepwater'; }
}

export function useBrand() {
  const [brand, setBrand] = useState(readBrand);
  useEffect(() => {
    try { localStorage.setItem(KEY, brand); } catch { /* storage unavailable; the brand still applies for this visit */ }
    document.documentElement.dataset.brand = brand;
    document.title = `${BRANDS[brand].name} · Learn the language, find the job`;
    window.dispatchEvent(new CustomEvent(EVENT, { detail: brand }));
  }, [brand]);
  useEffect(() => {
    const sync = event => setBrand(event.detail);
    window.addEventListener(EVENT, sync);
    return () => window.removeEventListener(EVENT, sync);
  }, []);
  return [brand, setBrand];
}

export function BrandSwitch() {
  const [brand, setBrand] = useBrand();
  return <div className="brand-switch" role="group" aria-label="Brand">
    {Object.values(BRANDS).map(option => <button key={option.id} type="button" aria-pressed={brand === option.id} onClick={() => setBrand(option.id)}>{option.name}</button>)}
  </div>;
}
