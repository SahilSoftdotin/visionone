import { BarChart3, FileText, LayoutDashboard, PhoneCall, TrendingUp, Users } from 'lucide-react';

/**
 * Six items. Not seven.
 *
 * A primary navigation item is added only when there is a Phase-1 user need that no existing
 * screen covers.
 */
export const CLIENT_NAVIGATION = [
  { to: 'overview', label: 'Overview', icon: LayoutDashboard, ready: true },
  { to: 'growth', label: 'Growth', icon: TrendingUp, ready: true },
  { to: 'leads', label: 'Leads', icon: Users, ready: true },
  { to: 'front-desk', label: 'Front Desk', icon: PhoneCall, ready: true },
  { to: 'work', label: 'Work & Content', icon: FileText, ready: true },
  { to: 'reports', label: 'Reports', icon: BarChart3, ready: true },
] as const;
