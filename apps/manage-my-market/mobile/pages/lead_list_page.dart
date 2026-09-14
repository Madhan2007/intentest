// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-14
// Description: Mobile Lead Pipeline list screen for Manage My Market.
import 'package:flutter/material.dart';
import 'package:opzhub_mobile/api/http_client.dart';

class LeadListPage extends StatefulWidget {
  const LeadListPage({super.key});

  @override
  State<LeadListPage> createState() => _LeadListPageState();
}

class _LeadListPageState extends State<LeadListPage> {
  List<dynamic> _leads = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadLeads();
  }

  Future<void> _loadLeads() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final response = await HttpOpzhub.post<dynamic>(
        '/manage-my-market/leads/read',
        {'limit': 50},
      );
      setState(() {
        _leads = response is List ? response : [];
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Color _statusColor(String? status) {
    switch (status) {
      case 'WON':
        return Colors.green;
      case 'NEW':
        return Colors.blue;
      case 'QUALIFIED':
        return Colors.purple;
      case 'PROPOSAL':
        return Colors.pink;
      case 'NEGOTIATION':
        return Colors.amber;
      case 'LOST':
        return Colors.red;
      case 'DISQUALIFIED':
        return Colors.grey;
      default:
        return Colors.cyan;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Marketing Leads'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadLeads,
          ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
              const SizedBox(height: 12),
              Text(
                'Failed to load leads:\n$_error',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _loadLeads,
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_leads.isEmpty) {
      return const Center(
        child: Text(
          'No leads found.',
          style: TextStyle(color: Colors.white60, fontSize: 16),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadLeads,
      child: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: _leads.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          final lead = _leads[index] as Map<String, dynamic>;
          final status = lead['status'] as String? ?? 'NEW';
          final code = lead['lead_code'] as String? ?? '';
          final name = lead['display_name'] as String? ?? 'Unnamed Lead';
          final company = lead['company_name'] as String?;
          final value = lead['estimated_value'];

          return Card(
            elevation: 2,
            color: const Color(0xFF171A21),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
              side: const BorderSide(color: Color(0xFF2A2F3A)),
            ),
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              title: Row(
                children: [
                  Text(
                    code,
                    style: const TextStyle(
                      color: Color(0xFF38BDF8),
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      name,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 6.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      company ?? (lead['lead_source_type'] as String? ?? ''),
                      style: const TextStyle(color: Colors.white60, fontSize: 12),
                    ),
                    if (value != null)
                      Text(
                        '\$$value',
                        style: const TextStyle(
                          color: Color(0xFF10B981),
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                  ],
                ),
              ),
              trailing: Chip(
                label: Text(
                  status,
                  style: TextStyle(
                    color: _statusColor(status),
                    fontWeight: FontWeight.bold,
                    fontSize: 11,
                  ),
                ),
                backgroundColor: _statusColor(status).withOpacity(0.15),
                side: BorderSide(color: _statusColor(status).withOpacity(0.3)),
                padding: EdgeInsets.zero,
                visualDensity: VisualDensity.compact,
              ),
              onTap: () {
                _showLeadDetails(context, lead);
              },
            ),
          );
        },
      ),
    );
  }

  void _showLeadDetails(BuildContext context, Map<String, dynamic> lead) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF171A21),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) {
        return Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                lead['display_name'] as String? ?? '',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
              ),
              const SizedBox(height: 4),
              Text(
                'Code: ${lead['lead_code']} • Source: ${lead['lead_source_type']}',
                style: const TextStyle(color: Color(0xFF38BDF8), fontSize: 13),
              ),
              const Divider(height: 24, color: Color(0xFF2A2F3A)),
              if (lead['email'] != null) Text('Email: ${lead['email']}', style: const TextStyle(color: Colors.white70)),
              if (lead['phone'] != null) Text('Phone: ${lead['phone']}', style: const TextStyle(color: Colors.white70)),
              if (lead['company_name'] != null) Text('Company: ${lead['company_name']}', style: const TextStyle(color: Colors.white70)),
              const SizedBox(height: 12),
              Text('Status: ${lead['status']}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
              const SizedBox(height: 16),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Close'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
