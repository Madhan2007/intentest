// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:flutter/foundation.dart';
import 'package:opzhub_mobile/api/http_client.dart';

/// Authenticated user details returned by the kernel session API.
class SessionUser {
  /// Stable user identifier.
  final String id;

  /// User-facing display name.
  final String displayName;

  /// Granted role identifiers for the current session.
  final List<String> roles;

  /// Creates a session user model.
  SessionUser({required this.id, required this.displayName, this.roles = const []});

  /// Builds a [SessionUser] from the shared backend response envelope.
  factory SessionUser.fromJson(Map<String, dynamic> json) => SessionUser(
        id: json['id'] as String,
        displayName: json['n'] as String,
        roles: (json['r'] as List?)?.cast<String>() ?? const [],
      );
}

/// Access grants keyed by application id and feature id.
typedef AccessMatrix = Map<String, Map<String, String>>;

/// Kernel session store (doc 18 §6, doc 03 §2 "Auth UI"). Views live in
/// modules/identity/mobile.
class SessionController extends ChangeNotifier {
  /// Current signed-in user, or `null` when unauthenticated.
  SessionUser? user;

  /// Current access grants.
  AccessMatrix matrix = {};

  /// Whether the initial session restore is still in progress.
  bool loading = true;

  /// Restores the active session from the configured kernel API.
  Future<void> restore() async {
    try {
      final data = await HttpOpzhub.get<Map<String, dynamic>>('/identity/session');
      user = SessionUser.fromJson(data['user'] as Map<String, dynamic>);
      matrix = _parseMatrix(data['a']);
    } catch (_) {
      user = null;
      matrix = {};
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  /// Signs in with a username and password.
  Future<void> login(String username, String password) async {
    final data = await HttpOpzhub.post<Map<String, dynamic>>('/identity/login', {
      'username': username,
      'password': password,
    });
    HttpOpzhub.setToken(data['token'] as String?);
    user = SessionUser.fromJson(data['user'] as Map<String, dynamic>);
    matrix = _parseMatrix(data['a']);
    notifyListeners();
  }

  /// Ends the active session and clears all in-memory auth state.
  Future<void> logout() async {
    try {
      await HttpOpzhub.post('/identity/logout');
    } finally {
      HttpOpzhub.setToken(null);
      user = null;
      matrix = {};
      notifyListeners();
    }
  }

  AccessMatrix _parseMatrix(dynamic raw) {
    final map = raw as Map<String, dynamic>? ?? {};
    return map.map(
      (app, features) => MapEntry(
        app,
        (features as Map<String, dynamic>).map(
          (key, value) => MapEntry(key, value as String),
        ),
      ),
    );
  }
}

/// Generic `has(app, feature, letter)` — kernel never hardcodes an app id (doc 18 §4.1).
bool hasAccess(AccessMatrix matrix, String app, String feature, String letter) {
  final grants = matrix[app];
  if (grants == null) return false;
  final letters = grants[feature] ?? grants['*'] ?? '';
  return letters.contains(letter);
}
