import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TaskService } from './task.service';

describe('TaskService', () => {
  let service: TaskService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TaskService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the newest tasks without filters', () => {
    service.browse({ categoryId: null, municipalityId: null, sort: 'newest', page: 0 }).subscribe();

    const request = http.expectOne((req) => req.url === '/api/tasks');
    expect(request.request.params.toString()).toBe('page=0&size=10&sort=publishedAt,desc');
  });

  it('sends the chosen filters, sort and page', () => {
    service.browse({ categoryId: 'c1', municipalityId: 'm1', sort: 'budgetHigh', page: 2 }).subscribe();

    const params = http.expectOne((req) => req.url === '/api/tasks').request.params;
    expect(params.get('categoryId')).toBe('c1');
    expect(params.get('municipalityId')).toBe('m1');
    expect(params.get('sort')).toBe('budget,desc');
    expect(params.get('page')).toBe('2');
  });
});
