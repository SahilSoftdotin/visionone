import {
  BarChart3,
  CalendarDays,
  FileText,
  LayoutDashboard,
  PhoneCall,
  TrendingUp,
  Users,
} from 'lucide-react';

/**
 * Seven items.
 *
 * The master prompt specifies six and says not to add a seventh without a strong Phase-1 need.
 * Calendar was requested directly. It sits beside Front Desk because appointments and calls are
 * the same conversation, and it is the one screen that answers "which day" rather than "how many".
 */
export const CLIENT_NAVIGATION = [
  { to: 'overview', label: 'Overview', icon: LayoutDashboard, ready: true },
  { to: 'growth', label: 'Growth', icon: TrendingUp, ready: true },
  { to: 'leads', label: 'Leads', icon: Users, ready: true },
  { to: 'front-desk', label: 'Front Desk', icon: PhoneCall, ready: true },
  { to: 'calendar', label: 'Calendar', icon: CalendarDays, ready: true },
  { to: 'work', label: 'Work & Content', icon: FileText, ready: true },
  { to: 'reports', label: 'Reports', icon: BarChart3, ready: true },
] as const;
