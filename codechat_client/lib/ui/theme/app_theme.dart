import 'package:flutter/material.dart';

class CodeChatTheme {
  static ThemeData get darkTheme {
    const primaryGreen = Color(0xFF00FF41);
    const bgBlack = Color(0xFF0A0A0A);
    const surfaceGrey = Color(0xFF141414);
    const cardGrey = Color(0xFF1E1E1E);
    const borderGrey = Color(0xFF2E2E2E);

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: bgBlack,
      colorScheme: const ColorScheme.dark(
        primary: primaryGreen,
        secondary: Color(0xFF8E8E93),
        surface: surfaceGrey,
        onSurface: Colors.white,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: surfaceGrey,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        iconTheme: IconThemeData(color: Colors.white70),
        titleTextStyle: TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.bold,
          fontSize: 18,
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: cardGrey,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: borderGrey),
        ),
      ),
      cardTheme: CardThemeData(
        color: cardGrey,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: borderGrey),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceGrey,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        hintStyle: const TextStyle(color: Colors.white38),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderGrey),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderGrey),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: primaryGreen),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primaryGreen,
          foregroundColor: Colors.black,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 14,
            letterSpacing: 1.1,
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: primaryGreen,
          textStyle: const TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
      textTheme: const TextTheme(
        headlineMedium: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 24),
        bodyLarge: TextStyle(color: Colors.white, fontSize: 16),
        bodyMedium: TextStyle(color: Colors.white70, fontSize: 14),
        bodySmall: TextStyle(color: Colors.white38, fontSize: 12),
      ),
    );
  }
}
