import Big from 'big.js';
import { Transactie } from '../api';
import { leesBedrag, naarApi, toonBedrag } from './bedrag';
import { BedragPipe } from './bedrag.pipe';
import { toonDatum, toonDatumregel, toonDatumTijd } from './datum';
import { groepeerPerDag } from './groepeer';
import { toonIban } from './iban';

describe('bedragen', () => {
  it('leest Nederlandse en API-notatie', () => {
    expect(naarApi(leesBedrag('12')!)).toBe('12.00');
    expect(naarApi(leesBedrag('12,5')!)).toBe('12.50');
    expect(naarApi(leesBedrag('1.234,56')!)).toBe('1234.56');
    expect(naarApi(leesBedrag('€ 63,48')!)).toBe('63.48');
    expect(naarApi(leesBedrag('63.48')!)).toBe('63.48');
  });

  it('weigert ongeldige bedragen', () => {
    for (const fout of ['', 'abc', '1,001', '-5', '12,', '1e3', null, undefined]) {
      expect(leesBedrag(fout)).toBeNull();
    }
  });

  it('rekent zonder afrondingsfouten', () => {
    expect(naarApi(new Big('0.10').plus('0.20'))).toBe('0.30');
  });

  it('toont bedragen op zijn Nederlands', () => {
    expect(toonBedrag('1842.17')).toBe('1.842,17');
    expect(toonBedrag('-63.48')).toBe('−63,48');
    expect(toonBedrag('3215.00', { teken: true })).toBe('+3.215,00');
    expect(toonBedrag('1234567.5', { valuta: true })).toBe('€ 1.234.567,50');
    expect(toonBedrag('0.00', { teken: true })).toBe('0,00');
    expect(new BedragPipe().transform('-0.5', 'teken', 'valuta')).toBe('−€ 0,50');
    expect(new BedragPipe().transform(undefined)).toBe('');
  });
});

describe('datums', () => {
  it('volgens het FO', () => {
    expect(toonDatumregel('2026-10-02')).toBe('Vrijdag 2 oktober 2026');
    expect(toonDatum('2026-10-03')).toBe('3 oktober 2026');
    expect(toonDatumTijd('2026-10-03T11:26:00Z')).toBe('3 oktober 2026 om 13:26');
    expect(toonDatumTijd('2026-01-15T12:05:00Z')).toBe('15 januari 2026 om 13:05');
  });
});

describe('IBAN', () => {
  it('in blokken van 4', () => {
    expect(toonIban('NL13SIMB0000100011')).toBe('NL13 SIMB 0000 1000 11');
    expect(toonIban(undefined)).toBe('');
  });
});

describe('groeperen', () => {
  const t = (id: string, datum: string): Transactie =>
    ({ id, datum, tijdstip: datum + 'T10:00:00Z', uitgevoerdOp: datum, tegenNaam: 'x', bedrag: '-1.00',
      type: 'INCASSO', typeLabel: 'Incasso', rekeninghouder: 'Jan', van: { naam: 'a' }, naar: { naam: 'b' } }) as Transactie;

  it('per dag, met jaartalregels bij de overgang naar een vorig jaar', () => {
    const dagen = groepeerPerDag([t('1', '2026-01-03'), t('2', '2026-01-03'), t('3', '2025-12-31'), t('4', '2025-12-30')], true);
    expect(dagen.map((d) => d.datum)).toEqual(['2026-01-03', '2025-12-31', '2025-12-30']);
    expect(dagen[0].transacties.length).toBe(2);
    expect(dagen[0].nieuwJaar).toBeUndefined();
    expect(dagen[1].nieuwJaar).toBe(2025);
    expect(dagen[2].nieuwJaar).toBeUndefined();
  });

  it('zonder jaartallen voor de betaalrekening', () => {
    expect(groepeerPerDag([t('1', '2026-01-03'), t('2', '2025-12-31')]).every((d) => d.nieuwJaar === undefined)).toBe(true);
  });
});
