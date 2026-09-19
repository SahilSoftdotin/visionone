import { BarChart3, FileText, LayoutDashboard, PhoneCall, TrendingUp, Users } from 'lucide-react';

/**
 * Six items. Not seven.
 *
 * A primary navigation item is added only when there is a Phase-1 user need that no existing
 * screen covers.
 */
export const CLIENT_NAVIGATION = [
  { to: 'overview', label: 'Overview', icon: LayoutDashboard, ready: true },
  { to: 'growth', label: 'Growth', icon: TrendingUp, ready: false },
  { to: 'leads', label: 'Leads', icon: Users, ready: false },
  { to: 'front-desk', label: 'Front Desk', icon: PhoneCall, ready: false },
  { to: 'work', label: 'Work & Content', icon: FileText, ready: false },
  { to: 'reports', label: 'Reports', icon: BarChart3, ready: false },
] as const;
