import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Offer } from '../../api/models';
import { currentLang } from '../../i18n/lang';
import { ToastService } from '../../shared/toast/toast.service';
import { OfferPriceDialog } from './offer-price-dialog';

describe('Offer price dialog', () => {
  let fixture: ComponentFixture<OfferPriceDialog>;
  let http: HttpTestingController;
  let saved: Offer[];

  beforeEach(async () => {
    saved = [];
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OfferPriceDialog);
    fixture.componentRef.setInput('offerId', 'o1');
    fixture.componentRef.setInput('currentPrice', 70);
    fixture.componentInstance.saved.subscribe((offer) => saved.push(offer));
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  function element(): HTMLElement {
    return fixture.nativeElement;
  }

  async function type(selector: string, value: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function save(): Promise<void> {
    element().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('refuses an empty price and the price the offer already has', async () => {
    await save();
    expect(element().querySelector('[role=alert]')!.textContent).toContain('Enter a price above zero');

    await type('#offer-new-price', '70');
    await save();
    expect(element().querySelector('[role=alert]')!.textContent).toContain('This is already your price');

    http.expectNone('/api/offers/o1');
  });

  it('saves the new price and posts the change with the note in the chat', async () => {
    await type('#offer-new-price', '60');
    await type('#offer-price-note', ' As agreed ');
    await save();

    const update = http.expectOne({ method: 'PUT', url: '/api/offers/o1' });
    expect(update.request.body).toEqual({ price: 60 });
    update.flush({ id: 'o1', price: 60, status: 'PENDING' });
    http.expectOne('/api/conversations/by-offer/o1').flush({ id: 'c1' });
    const message = http.expectOne({ method: 'POST', url: '/api/conversations/c1/messages' });
    expect(message.request.body.content).toBe('I changed my offer from 70 KM to 60 KM.\nAs agreed');
    message.flush({ id: 'm1' });
    await fixture.whenStable();

    expect(saved.map((offer) => offer.price)).toEqual([60]);
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Your offer is now 60 KM.');
  });

  it('writes the chat message in Bosnian and uses the open conversation directly', async () => {
    currentLang.set('bs');
    fixture.componentRef.setInput('conversationId', 'c9');
    await fixture.whenStable();
    await type('#offer-new-price', '85');
    await save();

    http.expectOne('/api/offers/o1').flush({ id: 'o1', price: 85, status: 'PENDING' });
    const message = http.expectOne({ method: 'POST', url: '/api/conversations/c9/messages' });
    expect(message.request.body.content).toBe('Promijenio/la sam ponudu s 70 KM na 85 KM.');
    message.flush({ id: 'm2' });
    await fixture.whenStable();

    expect(saved).toHaveLength(1);
  });

  it('shows why the server refused the change and keeps the dialog open', async () => {
    currentLang.set('bs');
    await type('#offer-new-price', '90');
    await save();

    http
      .expectOne('/api/offers/o1')
      .flush({ status: 400, detail: 'Only a pending offer can be changed' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();

    expect(element().querySelector('[role=alert]')!.textContent).toContain('Mijenjati se može samo ponuda na čekanju');
    expect(saved).toHaveLength(0);
  });
});
