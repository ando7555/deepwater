import React, { useRef, useState } from 'react';
import { request } from '../app/http.js';
import { uploadPhoto, usePhoto } from '../app/photos.js';
import { Button, Notice } from '../ui/index.jsx';
import { MAX_PHOTOS } from './profileTools.js';

/** The person's own photos on the Deepwater profile: add up to four, remove, and choose the first (the cover). */
export default function PhotoManager({ photos, onChange, note }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const reload = async () => onChange(await request('/api/v1/account/photos'));
  const run = async action => {
    setBusy(true); setError('');
    try { await action(); await reload(); } catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  const add = event => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (file) run(() => uploadPhoto(file));
  };

  return <div className="photo-manager">
    <div className="photo-grid" role="list" aria-label="Your photos">
      {photos.map((photo, index) => <OwnPhoto key={photo.id} photo={photo} first={index === 0} busy={busy}
        onRemove={() => run(() => request(`/api/v1/account/photos/${photo.id}`, { method: 'DELETE' }))}
        onFirst={() => run(() => request(`/api/v1/account/photos/${photo.id}/first`, { method: 'POST' }))} />)}
      {photos.length < MAX_PHOTOS && <button type="button" className="photo-add" disabled={busy} onClick={() => input.current?.click()}>
        <span aria-hidden="true">+</span>{busy ? 'Saving…' : 'Add a photo'}
      </button>}
    </div>
    <input ref={input} type="file" accept="image/jpeg,image/png,image/*" hidden onChange={add} />
    <p className="privacy-note">{note ?? `Up to ${MAX_PHOTOS} photos. Location and camera details are removed from every photo when you upload it.`}</p>
    {error && <Notice tone="error">{error}</Notice>}
  </div>;
}

function OwnPhoto({ photo, first, busy, onRemove, onFirst }) {
  const url = usePhoto(`/api/v1/account/photos/${photo.id}`);
  return <figure className="photo-tile" role="listitem">
    {url ? <img src={url} alt={first ? 'Your first photo' : 'Your photo'} /> : <span className="photo-placeholder" aria-hidden="true" />}
    <figcaption>
      {first ? <span className="pill">Cover</span> : <Button variant="link" disabled={busy} onClick={onFirst}>Make cover</Button>}
      <Button variant="link" disabled={busy} onClick={onRemove}>Remove</Button>
    </figcaption>
  </figure>;
}
