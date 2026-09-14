// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
// Kernel smoke test — full auth flow needs a running backend; this only
// verifies the app boots to the loading state without throwing.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opzhub_mobile/app/app.dart';

void main() {
  testWidgets('OpzApp boots to a loading indicator', (WidgetTester tester) async {
    await tester.pumpWidget(const OpzApp());
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });
}
