import { Pipe, PipeTransform } from '@angular/core';

type DateInput = string | number | Date | null | undefined;

const PARTS = {
  day: new Intl.DateTimeFormat('fr-FR', { day: 'numeric' }),
  monthLong: new Intl.DateTimeFormat('fr-FR', { month: 'long' }),
  monthShort: new Intl.DateTimeFormat('fr-FR', { month: 'short' }),
  year: new Intl.DateTimeFormat('fr-FR', { year: 'numeric' }),
};

/**
 * Date en français, au motif des pipes Angular (`d`, `MMM`, `MMMM`, `y`, `HH`, `mm`, `ss`) mais par
 * `Intl.DateTimeFormat` : sans données de locale dans le bundle initial. « 21 nov. 2026, 09:26 ».
 */
export function formatDate(value: DateInput, pattern: string): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const two = (n: number) => String(n).padStart(2, '0');
  return pattern.replace(/MMMM|MMM|HH|mm|ss|d|y/g, (token) => {
    switch (token) {
      case 'MMMM':
        return PARTS.monthLong.format(date);
      case 'MMM':
        return PARTS.monthShort.format(date);
      case 'HH':
        return two(date.getHours());
      case 'mm':
        return two(date.getMinutes());
      case 'ss':
        return two(date.getSeconds());
      case 'd':
        return PARTS.day.format(date);
      default:
        return PARTS.year.format(date);
    }
  });
}

/** `{{ v.createdAt | swDate: 'd MMM y, HH:mm' }}` : date en français. */
@Pipe({ name: 'swDate' })
export class SwDatePipe implements PipeTransform {
  transform(value: DateInput, pattern = 'd MMM y, HH:mm'): string {
    return formatDate(value, pattern);
  }
}
