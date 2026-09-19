/**
 * Synthetic appointment fixtures for the calendar.
 *
 * THESE ARE INVENTED, like the other demo modules. They mirror `appointment_reference`, which is
 * deliberately a reference rather than a copy of the practice's schedule: an id, a time, a broad
 * service category and the channel that produced it. No clinical detail crosses this line.
 *
 * Names are synthetic and reused from the lead fixtures so the two screens tell the same story -
 * a booked lead and its appointment are the same person.
 */

export type AppointmentStatus = 'BOOKED' | 'ATTENDED' | 'CANCELLED' | 'RESCHEDULED';

export interface AppointmentRef {
  id: string;
  /** ISO date, no time zone games: the practice's own day. */
  date: string;
  time: string;
  /** Synthetic. Never a real patient. */
  patient: string;
  /** Broad category from the fixed list, never a diagnosis or note. */
  serviceInterest: string;
  source: string;
  status: AppointmentStatus;
  /** Minutes. */
  duration: number;
}

export const appointmentsDemo: AppointmentRef[] = [
  {
    id: 'A-501',
    date: '2026-09-02',
    time: '10:30',
    patient: 'Dana Whitfield',
    serviceInterest: 'Hormone Therapy',
    source: 'Google Ads',
    status: 'ATTENDED',
    duration: 45,
  },
  {
    id: 'A-502',
    date: '2026-09-03',
    time: '14:00',
    patient: 'Gerald Simmons',
    serviceInterest: 'Longevity Panel',
    source: 'Google Ads',
    status: 'ATTENDED',
    duration: 60,
  },
  {
    id: 'A-503',
    date: '2026-09-04',
    time: '11:15',
    patient: 'Yvonne Petrakis',
    serviceInterest: 'IV Therapy',
    source: 'Organic Search',
    status: 'ATTENDED',
    duration: 30,
  },
  {
    id: 'A-504',
    date: '2026-09-08',
    time: '09:45',
    patient: 'Tom Bradbury',
    serviceInterest: 'Hormone Therapy',
    source: 'AI Front Desk',
    status: 'ATTENDED',
    duration: 45,
  },
  {
    id: 'A-505',
    date: '2026-09-08',
    time: '13:30',
    patient: 'Owen Castellano',
    serviceInterest: 'Peptide Therapy',
    source: 'AI Front Desk',
    status: 'ATTENDED',
    duration: 45,
  },
  {
    id: 'A-506',
    date: '2026-09-09',
    time: '15:00',
    patient: 'Beatrice Lin',
    serviceInterest: 'Weight Management',
    source: 'Organic Search',
    status: 'CANCELLED',
    duration: 30,
  },
  {
    id: 'A-507',
    date: '2026-09-10',
    time: '10:00',
    patient: 'Raymond Osei',
    serviceInterest: 'Weight Management',
    source: 'Google Ads',
    status: 'ATTENDED',
    duration: 30,
  },
  {
    id: 'A-508',
    date: '2026-09-11',
    time: '16:15',
    patient: 'Marcus Ellery',
    serviceInterest: 'Longevity Panel',
    source: 'Organic Search',
    status: 'RESCHEDULED',
    duration: 60,
  },
  {
    id: 'A-509',
    date: '2026-09-15',
    time: '10:30',
    patient: 'Priya Raghunathan',
    serviceInterest: 'Weight Management',
    source: 'Google Maps',
    status: 'BOOKED',
    duration: 30,
  },
  {
    id: 'A-510',
    date: '2026-09-15',
    time: '14:45',
    patient: 'Hector Villalobos',
    serviceInterest: 'Hormone Therapy',
    source: 'Instagram / Facebook',
    status: 'BOOKED',
    duration: 45,
  },
  {
    id: 'A-511',
    date: '2026-09-16',
    time: '09:15',
    patient: 'Alicia Moreno',
    serviceInterest: 'Peptide Therapy',
    source: 'Instagram / Facebook',
    status: 'BOOKED',
    duration: 45,
  },
  {
    id: 'A-512',
    date: '2026-09-17',
    time: '11:00',
    patient: 'Nadia Kowalski',
    serviceInterest: 'IV Therapy',
    source: 'Direct / Referral',
    status: 'BOOKED',
    duration: 30,
  },
  {
    id: 'A-513',
    date: '2026-09-17',
    time: '15:30',
    patient: 'Colette Duffy',
    serviceInterest: 'Longevity Panel',
    source: 'Direct / Referral',
    status: 'BOOKED',
    duration: 60,
  },
  {
    id: 'A-514',
    date: '2026-09-18',
    time: '10:00',
    patient: 'Simone Arquette',
    serviceInterest: 'Longevity Panel',
    source: 'Google Maps',
    status: 'BOOKED',
    duration: 60,
  },
  {
    id: 'A-515',
    date: '2026-09-22',
    time: '13:00',
    patient: 'Curtis Haywood',
    serviceInterest: 'Hormone Therapy',
    source: 'Google Ads',
    status: 'BOOKED',
    duration: 45,
  },
  {
    id: 'A-516',
    date: '2026-09-22',
    time: '16:00',
    patient: 'Dana Whitfield',
    serviceInterest: 'Hormone Therapy',
    source: 'Google Ads',
    status: 'BOOKED',
    duration: 30,
  },
  {
    id: 'A-517',
    date: '2026-09-23',
    time: '09:30',
    patient: 'Gerald Simmons',
    serviceInterest: 'Longevity Panel',
    source: 'Google Ads',
    status: 'BOOKED',
    duration: 60,
  },
  {
    id: 'A-518',
    date: '2026-09-24',
    time: '14:15',
    patient: 'Tom Bradbury',
    serviceInterest: 'Peptide Therapy',
    source: 'AI Front Desk',
    status: 'BOOKED',
    duration: 45,
  },
  {
    id: 'A-519',
    date: '2026-09-25',
    time: '11:45',
    patient: 'Beatrice Lin',
    serviceInterest: 'Weight Management',
    source: 'Organic Search',
    status: 'BOOKED',
    duration: 30,
  },
  {
    id: 'A-520',
    date: '2026-09-29',
    time: '10:15',
    patient: 'Owen Castellano',
    serviceInterest: 'Peptide Therapy',
    source: 'AI Front Desk',
    status: 'BOOKED',
    duration: 45,
  },
  {
    id: 'A-521',
    date: '2026-09-30',
    time: '15:00',
    patient: 'Priya Raghunathan',
    serviceInterest: 'IV Therapy',
    source: 'Google Maps',
    status: 'BOOKED',
    duration: 30,
  },
];

/** Appointments for one ISO date, ordered by time. */
export function appointmentsOn(date: string): AppointmentRef[] {
  return appointmentsDemo
    .filter((a) => a.date === date)
    .sort((a, b) => a.time.localeCompare(b.time));
}

/** ISO date key for a calendar cell, built locally so a UTC shift cannot move a day. */
export function isoDate(year: number, monthIndex: number, day: number): string {
  return `${year}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}
