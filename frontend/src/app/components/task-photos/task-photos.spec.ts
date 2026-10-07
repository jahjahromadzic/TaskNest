import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TaskDetail, TaskSummary } from '../../api/models';
import { currentLang } from '../../i18n/lang';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { PhotoPreparer } from '../../shared/photos/photos';
import { PhotoDropzone } from '../photo-dropzone/photo-dropzone';
import { TaskCard } from '../task-card/task-card';
import { TaskPhotos } from './task-photos';

function file(name: string, type: string, size = 1000): File {
  const photo = new File(['x'], name, { type });
  Object.defineProperty(photo, 'size', { value: size });
  return photo;
}

describe('Task photos', () => {
  let http: HttpTestingController;

  const task: TaskDetail = {
    id: 't1',
    title: 'Fix the sink',
    status: 'PUBLISHED',
    photos: [
      { id: 'p1', url: '/api/photos/p1', width: 800, height: 600 },
      { id: 'p2', url: '/api/photos/p2', width: 800, height: 600 },
      { id: 'p3', url: '/api/photos/p3', width: 800, height: 600 },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  async function open(detail: TaskDetail, editable: boolean) {
    const fixture = TestBed.createComponent(TaskPhotos);
    fixture.componentRef.setInput('task', detail);
    fixture.componentRef.setInput('editable', editable);
    await fixture.whenStable();
    return fixture;
  }

  it('shows the photos to visitors, without any controls to change them', async () => {
    const fixture = await open(task, false);

    const images = Array.from<HTMLImageElement>(fixture.nativeElement.querySelectorAll('section img'));
    expect(images.map((image) => image.getAttribute('src'))).toEqual(['/api/photos/p1', '/api/photos/p2', '/api/photos/p3']);
    expect(fixture.nativeElement.querySelector('app-photo-dropzone')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label="Remove photo"]')).toBeNull();
  });

  it('opens a photo full screen and moves through all of them, wrapping at the ends', async () => {
    const fixture = await open(task, false);

    fixture.nativeElement.querySelector('[aria-label="Open photo 3"]').click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(true);
    expect(fixture.nativeElement.querySelector('figcaption').textContent).toContain('3 of 3');

    fixture.nativeElement.querySelector('[aria-label="Next photo"]').click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('figure img').getAttribute('src')).toBe('/api/photos/p1');

    fixture.nativeElement.querySelector('dialog').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('figcaption').textContent).toContain('3 of 3');
  });

  it('lets the owner add photos one after another, prepared in the browser first', async () => {
    const prepared = new Blob(['jpeg'], { type: 'image/jpeg' });
    const prepare = vi.spyOn(TestBed.inject(PhotoPreparer), 'prepare').mockResolvedValue(prepared);
    const fixture = await open({ ...task, photos: [] }, true);

    const adding = fixture.componentInstance.add([file('a.jpg', 'image/jpeg'), file('b.png', 'image/png')]);
    await vi.waitFor(() => http.expectOne({ method: 'POST', url: '/api/tasks/t1/photos' }).flush({ id: 'n1', url: '/api/photos/n1' }));
    await vi.waitFor(() => http.expectOne({ method: 'POST', url: '/api/tasks/t1/photos' }).flush({ id: 'n2', url: '/api/photos/n2' }));
    await adding;
    await fixture.whenStable();

    expect(prepare).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.photos().map((photo) => photo.id)).toEqual(['n1', 'n2']);
  });

  it('removes a photo only after the owner confirms', async () => {
    const fixture = await open(task, true);

    fixture.nativeElement.querySelector('[aria-label="Remove photo"]').click();
    await fixture.whenStable();
    TestBed.inject(ConfirmService).answer(true);
    await vi.waitFor(() => http.expectOne({ method: 'DELETE', url: '/api/tasks/t1/photos/p1' }).flush(null));
    await fixture.whenStable();

    expect(fixture.componentInstance.photos().map((photo) => photo.id)).toEqual(['p2', 'p3']);
  });

  it('turns away files that are not photos, too large, or over the limit of five', () => {
    currentLang.set('bs');
    const fixture = TestBed.createComponent(PhotoDropzone);
    fixture.componentRef.setInput('remaining', 1);
    const picked = vi.fn();
    fixture.componentInstance.picked.subscribe(picked);

    fixture.componentInstance.take([
      file('notes.pdf', 'application/pdf'),
      file('huge.jpg', 'image/jpeg', 25 * 1024 * 1024),
      file('first.webp', 'image/webp'),
      file('second.jpg', 'image/jpeg'),
    ]);

    expect(picked).toHaveBeenCalledWith([expect.objectContaining({ name: 'first.webp' })]);
    expect(fixture.componentInstance.errors()).toEqual([
      'notes.pdf nije JPEG, PNG ili WebP slika',
      'huge.jpg je veća od 20 MB',
      'second.jpg nije dodana: oglas može imati 5 fotografija',
    ]);
  });

  it('puts the first photo on the task card as its cover', async () => {
    const fixture = TestBed.createComponent(TaskCard);
    fixture.componentInstance.task = { id: 't1', title: 'Fix the sink', coverPhotoId: 'p1' } as TaskSummary;
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('/api/photos/p1');
  });
});
