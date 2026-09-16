import { Pipe, PipeTransform } from '@angular/core';
import { badgeClass, label } from '../core/labels';

/** Turns an API enum code into its French label. */
@Pipe({ name: 'label', standalone: true })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return label(value);
  }
}

/** Badge colour class for a status-like enum value. */
@Pipe({ name: 'badgeClass', standalone: true })
export class BadgeClassPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return badgeClass(value);
  }
}

/** Short French date, e.g. 14 mars 2026. */
@Pipe({ name: 'dateFr', standalone: true })
export class DateFrPipe implements PipeTransform {
  transform(value: string | Date | null | undefined, withTime = false): string {
    if (!value) return '—';
    const date = typeof value === 'string' ? new Date(value) : value;
    if (isNaN(date.getTime())) return '—';
    const options: Intl.DateTimeFormatOptions = withTime
      ? { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }
      : { day: '2-digit', month: 'short', year: 'numeric' };
    return date.toLocaleDateString('fr-FR', options);
  }
}

/** Relative time ("il y a 3 h") for activity feeds and notifications. */
@Pipe({ name: 'timeAgo', standalone: true })
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string {
    if (!value) return '';
    const date = typeof value === 'string' ? new Date(value) : value;
    if (isNaN(date.getTime())) return '';

    const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
    if (seconds < 60) return "à l'instant";
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `il y a ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `il y a ${hours} h`;
    const days = Math.floor(hours / 24);
    if (days < 31) return `il y a ${days} j`;
    const months = Math.floor(days / 30);
    if (months < 12) return `il y a ${months} mois`;
    return `il y a ${Math.floor(months / 12)} an(s)`;
  }
}

/** Days remaining before a date, negative when it has passed. */
@Pipe({ name: 'daysLeft', standalone: true })
export class DaysLeftPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '—';
    const target = new Date(value);
    if (isNaN(target.getTime())) return '—';
    const days = Math.ceil((target.getTime() - Date.now()) / 86400000);
    if (days === 0) return "aujourd'hui";
    if (days > 0) return `dans ${days} j`;
    return `${Math.abs(days)} j de retard`;
  }
}

/** Human-readable file size. */
@Pipe({ name: 'fileSize', standalone: true })
export class FileSizePipe implements PipeTransform {
  transform(bytes: number | null | undefined): string {
    if (!bytes) return '—';
    const units = ['o', 'Ko', 'Mo', 'Go'];
    let size = bytes;
    let unit = 0;
    while (size >= 1024 && unit < units.length - 1) {
      size /= 1024;
      unit++;
    }
    return `${size.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
  }
}
