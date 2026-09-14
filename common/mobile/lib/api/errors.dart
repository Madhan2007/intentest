// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
/// Thrown by [HttpOpzhub] on `ok:false` kernel envelopes (doc 04 §4, doc 24).
class ApiException implements Exception {
  final String code;
  final String kind;
  final String msg;
  final String? hint;
  final String correlationId;

  ApiException({
    required this.code,
    required this.kind,
    required this.msg,
    this.hint,
    required this.correlationId,
  });

  @override
  String toString() => msg;
}
