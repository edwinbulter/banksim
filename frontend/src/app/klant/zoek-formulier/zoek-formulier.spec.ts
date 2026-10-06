import { TestBed } from '@angular/core/testing';
import { ZoekFormulier, Zoekcriteria } from './zoek-formulier';

describe('ZoekFormulier', () => {
  function maak() {
    const fixture = TestBed.createComponent(ZoekFormulier);
    const zoekopdrachten: Zoekcriteria[] = [];
    fixture.componentInstance.zoek.subscribe((c) => zoekopdrachten.push(c));
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const invul = (label: string, waarde: string) => {
      const veld = [...element.querySelectorAll('label')].find((l) => l.textContent?.includes(label))!
        .querySelector('input, select') as HTMLInputElement;
      veld.value = waarde;
      veld.dispatchEvent(new Event(veld.tagName === 'SELECT' ? 'change' : 'input'));
    };
    const klik = (tekst: string) =>
      ([...element.querySelectorAll('button')].find((b) => b.textContent?.includes(tekst)) as HTMLButtonElement).click();
    return { fixture, zoekopdrachten, invul, klik, element };
  }

  it('zet bedragen om naar het contractformaat', () => {
    const { fixture, zoekopdrachten, invul, klik } = maak();
    invul('Naam, bedrag', ' Eneco ');
    invul('Bedrag van', '10');
    invul('Bedrag t/m', '1.234,5');
    invul('Transactietype', 'INCASSO');
    klik('Zoeken');
    fixture.detectChanges();
    expect(zoekopdrachten).toEqual([{ tekst: 'Eneco', min: '10.00', max: '1234.50', type: 'INCASSO', richting: 'ALL' }]);
  });

  it('weigert een ongeldig bedragbereik', () => {
    const { fixture, zoekopdrachten, invul, klik, element } = maak();
    invul('Bedrag van', '50');
    invul('Bedrag t/m', '20');
    klik('Zoeken');
    fixture.detectChanges();
    expect(zoekopdrachten).toEqual([]);
    expect(element.textContent).toContain('"Bedrag van" mag niet groter zijn dan "Bedrag t/m".');
  });

  it('Wissen maakt alles leeg', () => {
    const { fixture, invul, klik, element } = maak();
    let gewist = false;
    fixture.componentInstance.wis.subscribe(() => (gewist = true));
    invul('Naam, bedrag', 'x');
    klik('Wissen');
    expect(gewist).toBe(true);
    expect((element.querySelector('input[type=search]') as HTMLInputElement).value).toBe('');
  });
});
