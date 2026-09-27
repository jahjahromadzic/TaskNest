import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReferenceService } from './reference.service';
import { Category } from '../api/models';

describe('ReferenceService', () => {
  let service: ReferenceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReferenceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads categories from the API', () => {
    const categories: Category[] = [{ id: 'a1', name: 'Plumbing' }];
    let received: Category[] | undefined;

    service.getCategories().subscribe((result) => (received = result));

    const request = http.expectOne('/api/categories');
    expect(request.request.method).toBe('GET');
    request.flush(categories);

    expect(received).toEqual(categories);
  });
});
