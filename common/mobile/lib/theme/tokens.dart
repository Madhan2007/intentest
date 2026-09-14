// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:flutter/material.dart';

/// Same token names as web (doc 03 §2.1); `gui.mode: lite` only in this pass.
class OpzTokens {
  static const colorBg = Color(0xFF0F1115);
  static const colorSurface = Color(0xFF171A21);
  static const colorBorder = Color(0xFF2A2F3A);
  static const colorText = Color(0xFFE6E8EB);
  static const colorPrimary = Color(0xFF4F7CFF);
}

/// Builds the shared Material theme for the mobile shell.
ThemeData buildOpzTheme() {
  return ThemeData(
    brightness: Brightness.dark,
    scaffoldBackgroundColor: OpzTokens.colorBg,
    colorScheme: ColorScheme.fromSeed(
      seedColor: OpzTokens.colorPrimary,
      brightness: Brightness.dark,
    ),
    useMaterial3: true,
  );
}
