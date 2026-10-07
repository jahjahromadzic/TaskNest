import { Injectable } from '@angular/core';

export const MAX_PHOTOS = 5;
export const MAX_SOURCE_BYTES = 20 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const MAX_EDGE = 1920;
const JPEG_QUALITY = 0.85;

export type PhotoProblem = 'unsupported' | 'tooBig';

export function photoUrl(id: string | undefined): string {
  return `/api/photos/${id}`;
}

export function photoProblem(file: File): PhotoProblem | null {
  if (!ACCEPTED_TYPES.includes(file.type)) {
    return 'unsupported';
  }
  return file.size > MAX_SOURCE_BYTES ? 'tooBig' : null;
}

@Injectable({ providedIn: 'root' })
export class PhotoPreparer {
  async prepare(file: File): Promise<Blob> {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();

    return new Promise((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Encoding the photo failed'))), 'image/jpeg', JPEG_QUALITY),
    );
  }
}
