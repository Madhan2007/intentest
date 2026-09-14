// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-14
// Description: Manage My Market mobile module plugin.
import 'package:opzhub_mobile/kernel/types.dart';
import 'pages/lead_list_page.dart';
import 'pages/call_queue_page.dart';

class ManageMyMarketPlugin implements ModulePlugin {
  @override
  void register(KernelApp app) {
    app.addRoutes([
      RouteDef(path: '/market/leads', builder: (context) => const LeadListPage()),
      RouteDef(path: '/market/calls', builder: (context) => const CallQueuePage()),
    ]);

    app.addMenuItems([
      const MenuItem(id: 'market.leads', label: 'Leads', path: '/market/leads'),
      const MenuItem(id: 'market.calls', label: 'Call Queue', path: '/market/calls'),
    ]);
  }
}
