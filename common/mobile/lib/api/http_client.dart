// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:flutter/foundation.dart';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:opzhub_mobile/api/errors.dart';

/// Public origin for non-web builds.
///
/// Configure with `--dart-define=OPZHUB_ORIGIN=https://host:port`.
const String opzhubOrigin = String.fromEnvironment('OPZHUB_ORIGIN');

/// Shared API prefix for the kernel HTTP surface.
const String opzhubApiBasePath = String.fromEnvironment(
  'OPZHUB_API_BASE_PATH',
  defaultValue: '/api/v1/opzhub',
);

/// HTTP client to the Java ERP engine (doc 03 §14). Session id is a Bearer
/// token held in memory for this pass — secure-storage persistence is a
/// follow-up item (doc 18 §6 "Flutter: Bearer from secure storage").
class HttpOpzhub {
  static String? _token;

  /// Updates the in-memory bearer token used by subsequent requests.
  static void setToken(String? token) => _token = token;

  static Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  /// Sends a `GET` request to the configured kernel API.
  static Future<T> get<T>(String path) => _request<T>('GET', path);

  /// Sends a `POST` request to the configured kernel API.
  static Future<T> post<T>(String path, [Map<String, dynamic>? body]) =>
      _request<T>('POST', path, body);

  static Future<T> _request<T>(
    String method,
    String path, [
    Map<String, dynamic>? body,
  ]) async {
    final uri = _buildUri(path);
    final response = method == 'GET'
        ? await http.get(uri, headers: _headers)
        : await http.post(
            uri,
            headers: _headers,
            body: body == null ? null : jsonEncode(body),
          );

    final decoded = response.body.isEmpty
        ? null
        : jsonDecode(response.body) as Map<String, dynamic>;
    final ok = decoded?['ok'] == true;
    if (!ok) {
      final error = decoded?['error'] as Map<String, dynamic>?;
      throw ApiException(
        code: error?['code'] ?? 'unknown_error',
        kind: error?['kind'] ?? 'unknown_error',
        msg: error?['msg'] ?? 'Request failed (${response.statusCode})',
        hint: error?['hint'],
        correlationId: decoded?['correlation_id'] ?? '',
      );
    }
    return decoded?['data'] as T;
  }

  static Uri _buildUri(String path) {
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    final normalizedBasePath = opzhubApiBasePath.startsWith('/')
        ? opzhubApiBasePath
        : '/$opzhubApiBasePath';
    final requestPath = '$normalizedBasePath$normalizedPath';

    if (kIsWeb && opzhubOrigin.isEmpty) {
      return Uri.base.resolve(requestPath);
    }
    if (opzhubOrigin.isEmpty) {
      throw StateError(
        'Missing OPZHUB_ORIGIN dart-define for non-web builds.',
      );
    }
    return Uri.parse(opzhubOrigin).resolve(requestPath);
  }
}
