// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-14
// Description: Mobile Telecalling Queue screen for Manage My Market.
import 'package:flutter/material.dart';
import 'package:opzhub_mobile/api/http_client.dart';

class CallQueuePage extends StatefulWidget {
  const CallQueuePage({super.key});

  @override
  State<CallQueuePage> createState() => _CallQueuePageState();
}

class _CallQueuePageState extends State<CallQueuePage> {
  List<dynamic> _queues = [];
  String? _selectedQueueId;
  List<dynamic> _items = [];
  Map<String, dynamic>? _currentItem;
  bool _loading = true;
  String? _error;

  String _selectedOutcome = 'CONNECTED';
  final TextEditingController _notesController = TextEditingController();
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _loadQueues();
  }

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _loadQueues() async {
    setState(() => _loading = true);
    try {
      final res = await HttpOpzhub.post<dynamic>(
        '/manage-my-market/calls/queues/read',
        {'status': 'ACTIVE'},
      );
      final list = res is List ? res : [];
      setState(() {
        _queues = list;
        if (list.isNotEmpty) {
          _selectedQueueId = list.first['id'] as String?;
        }
        _loading = false;
      });

      if (_selectedQueueId != null) {
        _loadQueueItems(_selectedQueueId!);
      }
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _loadQueueItems(String queueId) async {
    try {
      final res = await HttpOpzhub.post<dynamic>(
        '/manage-my-market/calls/queues/$queueId/items/read',
        {'status': 'PENDING'},
      );
      final list = res is List ? res : [];
      setState(() {
        _items = list;
        _currentItem = list.isNotEmpty ? list.first as Map<String, dynamic> : null;
      });
    } catch (_) {}
  }

  Future<void> _logCallDisposition() async {
    if (_currentItem == null) return;
    setState(() => _submitting = true);

    try {
      await HttpOpzhub.post<dynamic>(
        '/manage-my-market/calls/logs',
        {
          'leadId': _currentItem!['lead_id'],
          'callOutcome': _selectedOutcome,
          'callDurationSeconds': 90,
          'notes': _notesController.text,
        },
      );

      final itemId = _currentItem!['id'] as String;
      await HttpOpzhub.post<dynamic>(
        '/manage-my-market/calls/items/$itemId',
        {'status': 'COMPLETED'},
      );

      _notesController.clear();
      setState(() {
        _items.removeWhere((item) => item['id'] == itemId);
        _currentItem = _items.isNotEmpty ? _items.first as Map<String, dynamic> : null;
        _submitting = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Call logged successfully! Next lead loaded.')),
        );
      }
    } catch (e) {
      setState(() => _submitting = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Telecaller Queue'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text('Error: $_error'))
              : _buildQueueContent(),
    );
  }

  Widget _buildQueueContent() {
    if (_currentItem == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.check_circle_outline, size: 64, color: Colors.greenAccent),
              const SizedBox(height: 16),
              const Text(
                'All Queued Calls Done!',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
              ),
              const SizedBox(height: 8),
              Text(
                'No pending contacts in this call queue.',
                style: TextStyle(color: Colors.grey.shade400),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _loadQueues,
                child: const Text('Refresh Queues'),
              ),
            ],
          ),
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Queue progress banner
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFF171A21),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFF2A2F3A)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Pending in Queue: ${_items.length}',
                  style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
                ),
                Text(
                  'Priority: ${_currentItem!['priority'] ?? 'MEDIUM'}',
                  style: const TextStyle(color: Color(0xFFF59E0B), fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // Contact details card
          Card(
            color: const Color(0xFF171A21),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
              side: const BorderSide(color: Color(0xFF2A2F3A)),
            ),
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('TARGET CONTACT', style: TextStyle(color: Colors.grey, fontSize: 11, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  Text(
                    'Lead #${_currentItem!['lead_id'].toString().substring(0, 8)}',
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton.icon(
                    onPressed: () {},
                    icon: const Icon(Icons.phone),
                    label: const Text('Dial Lead Now'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                      foregroundColor: Colors.white,
                      minimumSize: const Size.fromHeight(44),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Outcome selector
          const Text('Call Disposition Outcome', style: TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: _selectedOutcome,
            dropdownColor: const Color(0xFF171A21),
            decoration: InputDecoration(
              filled: true,
              fillColor: const Color(0xFF171A21),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: Color(0xFF2A2F3A)),
              ),
            ),
            items: const [
              DropdownMenuItem(value: 'CONNECTED', child: Text('Connected / In Discussion')),
              DropdownMenuItem(value: 'INTERESTED', child: Text('Interested - Qualified')),
              DropdownMenuItem(value: 'NOT_INTERESTED', child: Text('Not Interested')),
              DropdownMenuItem(value: 'NO_ANSWER', child: Text('No Answer')),
              DropdownMenuItem(value: 'CALLBACK_REQUESTED', child: Text('Callback Requested')),
              DropdownMenuItem(value: 'WRONG_NUMBER', child: Text('Wrong Number')),
            ],
            onChanged: (val) {
              if (val != null) setState(() => _selectedOutcome = val);
            },
          ),
          const SizedBox(height: 16),

          // Notes
          const Text('Agent Notes', style: TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          TextField(
            controller: _notesController,
            maxLines: 3,
            decoration: InputDecoration(
              hintText: 'Customer response, next steps...',
              hintStyle: const TextStyle(color: Colors.white38),
              filled: true,
              fillColor: const Color(0xFF171A21),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: Color(0xFF2A2F3A)),
              ),
            ),
          ),
          const SizedBox(height: 20),

          ElevatedButton(
            onPressed: _submitting ? null : _logCallDisposition,
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF3B82F6),
              foregroundColor: Colors.white,
              minimumSize: const Size.fromHeight(48),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            child: _submitting
                ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Text('Log Disposition & Next Call', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
