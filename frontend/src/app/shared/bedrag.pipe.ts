import { Pipe, PipeTransform } from '@angular/core';
import { toonBedrag } from './bedrag';

/** {{ '-63.48' | bedrag }} → "−63,48"; met 'teken' ook "+" bij bijschrijvingen; met 'valuta' "€ " ervoor. */
@Pipe({ name: 'bedrag' })
export class BedragPipe implements PipeTransform {
  transform(waarde: string | null | undefined, ...opties: ('teken' | 'valuta')[]): string {
    if (waarde === null || waarde === undefined || waarde === '') {
      return '';
    }
    return toonBedrag(waarde, { teken: opties.includes('teken'), valuta: opties.includes('valuta') });
  }
}
