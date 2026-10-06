/** "NL13SIMB0000100011" → "NL13 SIMB 0000 1000 11". */
export function toonIban(iban: string | null | undefined): string {
  if (!iban) {
    return '';
  }
  return iban.replace(/\s/g, '').toUpperCase().replace(/(.{4})(?=.)/g, '$1 ');
}
