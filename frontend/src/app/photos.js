import { useEffect, useState } from 'react';
import { API, request } from './http.js';
import { session } from './storage.js';
import { fitWithin } from '../profile/profileTools.js';

/*
 * Photos are behind sign-in (the API checks who may see each one), so an <img src> cannot load them directly.
 * usePhoto fetches the image with the session token and hands the <img> a local blob: URL.
 */

export function usePhoto(path) {
  const [url, setUrl] = useState(null);
  useEffect(() => {
    if (!path) { setUrl(null); return undefined; }
    let objectUrl = null;
    let cancelled = false;
    const token = session.token();
    fetch(`${API}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
      .then(response => (response.ok ? response.blob() : null))
      .then(blob => {
        if (!blob || cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => {});
    return () => { cancelled = true; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [path]);
  return url;
}

/**
 * Shrinks a chosen photo to at most 1080 px and re-draws it as a JPEG before upload. Drawing it again also drops
 * its EXIF data (such as the GPS position) on the phone already; the server re-encodes it once more anyway.
 * Formats the browser cannot draw (some HEIC photos) are sent as they are, and the server says if it cannot read them.
 */
export async function prepareUpload(file, max = 1080) {
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') return file;
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const size = fitWithin(bitmap.width, bitmap.height, max);
    const canvas = document.createElement('canvas');
    canvas.width = size.width;
    canvas.height = size.height;
    const context = canvas.getContext('2d');
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, size.width, size.height);
    context.drawImage(bitmap, 0, 0, size.width, size.height);
    bitmap.close?.();
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.88));
    return blob ?? file;
  } catch {
    return file;
  }
}

export async function uploadPhoto(file) {
  const form = new FormData();
  form.append('photo', await prepareUpload(file), 'photo.jpg');
  return request('/api/v1/account/photos', { method: 'POST', form });
}
